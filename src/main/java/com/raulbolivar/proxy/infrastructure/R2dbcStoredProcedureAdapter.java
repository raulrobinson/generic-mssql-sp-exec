package com.raulbolivar.proxy.infrastructure;

import com.raulbolivar.proxy.application.StoredProcedureGateway;
import com.raulbolivar.proxy.config.StoredProcedureProperties;
import com.raulbolivar.proxy.domain.ExecuteProcedureCommand;
import com.raulbolivar.proxy.domain.ProcedureDefinition;
import com.raulbolivar.proxy.domain.ProcedureExecutionResult;
import com.raulbolivar.proxy.domain.ProcedureParameter;
import com.raulbolivar.proxy.helper.XmlOutputConverter;
import io.r2dbc.spi.*;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.temporal.TemporalAccessor;
import java.util.*;
import java.util.function.Function;

@Repository
public class R2dbcStoredProcedureAdapter implements StoredProcedureGateway {

    private static final String OUTPUT_PREFIX = "__sp_out__";
    private static final String BINDING_PREFIX = "P";
    private static final String OUTPUT_VARIABLE_PREFIX = "@__out_";
    private static final String INPUT_VARIABLE_PREFIX = "@__in_";

    private static final String SQL_DESCRIBE = """
            SELECT p.parameter_id, p.name, t.name AS type_name, p.max_length,
                   p.precision, p.scale, p.is_output, p.is_nullable, p.has_default_value
            FROM sys.procedures sp
            INNER JOIN sys.schemas s ON s.schema_id = sp.schema_id
            INNER JOIN sys.parameters p ON p.object_id = sp.object_id
            INNER JOIN sys.types t ON t.user_type_id = p.user_type_id
            WHERE s.name = :schema
              AND sp.name = :procedure
              AND p.parameter_id > 0
            ORDER BY p.parameter_id
            """;

    private static final String SQL_EXISTS = """
            SELECT COUNT(*) AS total
            FROM sys.procedures p
            INNER JOIN sys.schemas s ON s.schema_id = p.schema_id
            WHERE s.name = :schema AND p.name = :procedure
            """;

    private static final String SQL_ALLOWED = """
            SELECT s.name + '.' + p.name AS procedure_name
            FROM sys.procedures p
            INNER JOIN sys.schemas s ON s.schema_id = p.schema_id
            WHERE p.is_ms_shipped = 0
            ORDER BY s.name, p.name
            """;

    private final ConnectionFactoryRegistry registry;
    private final StoredProcedureProperties properties;
    private final R2dbcTypeMapper typeMapper;
    private final XmlOutputConverter xmlOutputConverter;

    public R2dbcStoredProcedureAdapter(ConnectionFactoryRegistry registry,
                                       StoredProcedureProperties properties,
                                       R2dbcTypeMapper typeMapper,
                                       XmlOutputConverter xmlOutputConverter) {
        this.registry = registry;
        this.properties = properties;
        this.typeMapper = typeMapper;
        this.xmlOutputConverter = xmlOutputConverter;
    }

    @Override
    public Mono<ProcedureDefinition> describe(String databaseKey, String schema, String procedure) {
        DatabaseClient client = registry.getDatabaseClient(databaseKey);

        return client.sql(SQL_DESCRIBE)
                .bind("schema", schema)
                .bind("procedure", procedure)
                .map((row, metadata) -> mapParameter(row))
                .all()
                .collectList()
                .flatMap(parameters -> {
                    if (!parameters.isEmpty()) {
                        return Mono.just(new ProcedureDefinition(schema, procedure, parameters));
                    }

                    return exists(client, schema, procedure)
                            .flatMap(found -> found
                                    ? Mono.just(new ProcedureDefinition(schema, procedure, List.of()))
                                    : Mono.error(new NoSuchElementException(
                                            "No existe el procedimiento " + schema + "." + procedure
                                                    + " en la base " + databaseKey)));
                })
                .onErrorMap(this::mapException);
    }

    @Override
    public Mono<ProcedureExecutionResult> execute(ExecuteProcedureCommand command) {
        ConnectionFactory connectionFactory = registry.getConnectionFactory(command.databaseKey());

        return describe(command.databaseKey(), command.schema(), command.procedure())
                .flatMap(definition -> {
                    validateInputParameters(definition, command.parameters());
                    return withConnection(connectionFactory,
                            connection -> execute(connection, command, definition));
                })
                .onErrorMap(this::mapException);
    }

    @Override
    public Flux<String> allowedProcedures(String databaseKey) {
        if (properties.allowed() != null && !properties.allowed().isEmpty()) {
            return Flux.fromIterable(properties.allowed()).sort();
        }

        return registry.getDatabaseClient(databaseKey)
                .sql(SQL_ALLOWED)
                .map((row, metadata) -> row.get("procedure_name", String.class))
                .all()
                .onErrorMap(this::mapException);
    }

    private Mono<ProcedureExecutionResult> execute(Connection connection,
                                                   ExecuteProcedureCommand command,
                                                   ProcedureDefinition definition) {
        ExecutionSql executionSql = buildExecutionSql(definition, command.parameters());
        Statement statement = connection.createStatement(executionSql.sql());
        bind(statement, executionSql.bindings());

        return Flux.from(statement.execute())
                .concatMap(this::consumeResult)
                .collectList()
                .map(results -> aggregate(command, definition, results))
                .timeout(properties.timeout());
    }

    private Mono<ResultData> consumeResult(Result result) {
        return Flux.from(result.flatMap(segment -> {
                    if (segment instanceof Result.RowSegment rowSegment) {
                        Row row = rowSegment.row();
                        return Mono.just(new SegmentData.RowData(readRow(row, row.getMetadata())));
                    }

                    if (segment instanceof Result.UpdateCount updateCount) {
                        return Mono.just(new SegmentData.UpdateData(Math.toIntExact(updateCount.value())));
                    }

                    return Mono.empty();
                }))
                .collectList()
                .map(segments -> {
                    List<Map<String, Object>> rows = segments.stream()
                            .filter(SegmentData.RowData.class::isInstance)
                            .map(SegmentData.RowData.class::cast)
                            .map(SegmentData.RowData::row)
                            .limit(properties.maxRows())
                            .toList();

                    List<Integer> updateCounts = segments.stream()
                            .filter(SegmentData.UpdateData.class::isInstance)
                            .map(SegmentData.UpdateData.class::cast)
                            .map(SegmentData.UpdateData::count)
                            .toList();

                    return new ResultData(rows, updateCounts);
                });
    }

    private ProcedureExecutionResult aggregate(ExecuteProcedureCommand command,
                                               ProcedureDefinition definition,
                                               List<ResultData> results) {
        Map<String, Object> outputs = new LinkedHashMap<>();
        List<List<Map<String, Object>>> resultSets = new ArrayList<>();
        List<Integer> updateCounts = new ArrayList<>();
        Map<String, ProcedureParameter> outputDefinitions = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        definition.parameters().stream()
                .filter(ProcedureParameter::output)
                .forEach(parameter -> outputDefinitions.put(parameter.name(), parameter));

        for (ResultData result : results) {
            updateCounts.addAll(result.updateCounts());
            if (result.rows().isEmpty()) continue;

            Map<String, Object> first = result.rows().getFirst();
            boolean outputResult = !first.isEmpty()
                    && first.keySet().stream().allMatch(key -> key.startsWith(OUTPUT_PREFIX));

            if (!outputResult) {
                resultSets.add(result.rows());
                continue;
            }

            first.forEach((key, value) -> {
                String parameterName = key.substring(OUTPUT_PREFIX.length());
                outputs.put(parameterName,
                        normalizeOutput(value, outputDefinitions.get(parameterName)));
            });
        }

        return new ProcedureExecutionResult(
                command.databaseKey(), command.schema(), command.procedure(),
                outputs, resultSets, updateCounts
        );
    }

    private Object normalizeOutput(Object value, ProcedureParameter parameter) {
        if (value == null || parameter == null) return value;

        if ("xml".equalsIgnoreCase(parameter.sqlTypeName()) && value instanceof String xml) {
            return xmlOutputConverter.toJsonObject(xml);
        }

        return normalize(value);
    }

    private ExecutionSql buildExecutionSql(ProcedureDefinition definition,
                                           Map<String, Object> input) {
        StringBuilder sql = new StringBuilder("SET NOCOUNT ON;\n");
        List<BindingValue> bindings = new ArrayList<>();
        List<String> arguments = new ArrayList<>();
        List<ProcedureParameter> outputs = definition.parameters().stream()
                .filter(ProcedureParameter::output)
                .toList();

        int bindingIndex = 0;

        for (ProcedureParameter parameter : definition.parameters()) {
            boolean supplied = containsIgnoreCase(input, parameter.name());

            if (parameter.output()) {
                String outputVariable = outputVariable(parameter);

                sql.append("DECLARE ").append(outputVariable).append(' ')
                        .append(sqlDeclaration(parameter)).append(";\n");

                if (supplied) {
                    String marker = BINDING_PREFIX + bindingIndex++;
                    Object value = findIgnoreCase(input, parameter.name());

                    if (isXml(parameter)) {
                        sql.append("SET ").append(outputVariable)
                                .append(" = CONVERT(xml, @").append(marker).append(");\n");
                    } else {
                        sql.append("SET ").append(outputVariable)
                                .append(" = @").append(marker).append(";\n");
                    }

                    bindings.add(new BindingValue(marker, value, parameter, isXml(parameter)));
                }

                arguments.add(quotedParameter(parameter.name())
                        + " = " + outputVariable + " OUTPUT");
                continue;
            }

            if (supplied) {
                String marker = BINDING_PREFIX + bindingIndex++;
                Object value = findIgnoreCase(input, parameter.name());

                if (isXml(parameter)) {
                    String inputVariable = inputVariable(parameter);
                    sql.append("DECLARE ").append(inputVariable).append(" xml;\n")
                            .append("SET ").append(inputVariable)
                            .append(" = CONVERT(xml, @").append(marker).append(");\n");

                    arguments.add(quotedParameter(parameter.name()) + " = " + inputVariable);
                    bindings.add(new BindingValue(marker, value, parameter, true));
                } else {
                    arguments.add(quotedParameter(parameter.name()) + " = @" + marker);
                    bindings.add(new BindingValue(marker, value, parameter, false));
                }

                continue;
            }

            if (!parameter.hasDefault()) {
                if (isXml(parameter)) {
                    String inputVariable = inputVariable(parameter);
                    sql.append("DECLARE ").append(inputVariable).append(" xml;\n")
                            .append("SET ").append(inputVariable).append(" = NULL;\n");
                    arguments.add(quotedParameter(parameter.name()) + " = " + inputVariable);
                } else {
                    String marker = BINDING_PREFIX + bindingIndex++;
                    arguments.add(quotedParameter(parameter.name()) + " = @" + marker);
                    bindings.add(new BindingValue(marker, null, parameter, false));
                }
            }
        }

        sql.append("EXEC ").append(quotedIdentifier(definition.schema()))
                .append('.').append(quotedIdentifier(definition.name()));

        if (!arguments.isEmpty()) sql.append("\n    ").append(String.join(",\n    ", arguments));
        sql.append(";\n");

        if (!outputs.isEmpty()) {
            sql.append("SELECT\n    ");
            for (int index = 0; index < outputs.size(); index++) {
                if (index > 0) sql.append(",\n    ");
                sql.append(outputSelectExpression(outputs.get(index)));
            }
            sql.append(";\n");
        }

        return new ExecutionSql(sql.toString(), bindings);
    }

    private String outputSelectExpression(ProcedureParameter parameter) {
        String variable = outputVariable(parameter);
        String alias = quotedIdentifier(OUTPUT_PREFIX + parameter.name());

        return switch (normalizedType(parameter)) {
            case "xml" -> "CAST(" + variable + " AS nvarchar(max)) AS " + alias;
            case "sql_variant" -> "CONVERT(nvarchar(max), " + variable + ") AS " + alias;
            case "geometry", "geography", "hierarchyid" ->
                    "CASE WHEN " + variable + " IS NULL THEN NULL ELSE "
                            + variable + ".ToString() END AS " + alias;
            default -> variable + " AS " + alias;
        };
    }

    private void bind(Statement statement, List<BindingValue> bindings) {
        for (BindingValue binding : bindings) {
            Object converted = binding.bindAsString()
                    ? binding.value() == null ? null : binding.value().toString()
                    : typeMapper.convert(binding.value(), binding.parameter());

            if (converted == null) {
                Class<?> nullType = binding.bindAsString()
                        ? String.class
                        : typeMapper.nullType(binding.parameter());
                statement.bindNull(binding.marker(), nullType);
            } else {
                statement.bind(binding.marker(), converted);
            }
        }
    }

    private Mono<Boolean> exists(DatabaseClient client, String schema, String procedure) {
        return client.sql(SQL_EXISTS)
                .bind("schema", schema)
                .bind("procedure", procedure)
                .map((row, metadata) -> number(row.get("total")))
                .one()
                .map(total -> total > 0)
                .defaultIfEmpty(false);
    }

    private ProcedureParameter mapParameter(Row row) {
        return new ProcedureParameter(
                number(row.get("parameter_id")),
                stripAt(row.get("name", String.class)),
                row.get("type_name", String.class),
                0,
                number(row.get("max_length")),
                number(row.get("precision")),
                number(row.get("scale")),
                bool(row.get("is_output")),
                bool(row.get("is_nullable")),
                bool(row.get("has_default_value"))
        );
    }

    private Map<String, Object> readRow(Row row, RowMetadata metadata) {
        Map<String, Object> values = new LinkedHashMap<>();

        metadata.getColumnMetadatas().forEach(column -> {
            String name = column.getName();

            try {
                values.put(name, normalize(row.get(name)));
            } catch (RuntimeException exception) {
                throw new StoredProcedureExecutionException(
                        0, null,
                        "No fue posible decodificar la columna '" + name
                                + "'. El Stored Procedure puede retornar un tipo no soportado "
                                + "por r2dbc-mssql, como xml, sql_variant, geometry, geography o hierarchyid.",
                        exception
                );
            }
        });

        return values;
    }

    private Object normalize(Object value) {
        if (value instanceof byte[] bytes) return Base64.getEncoder().encodeToString(bytes);
        if (value instanceof TemporalAccessor temporal) return temporal.toString();
        return value;
    }

    private String sqlDeclaration(ProcedureParameter parameter) {
        String type = normalizedType(parameter);

        return switch (type) {
            case "varchar", "char", "varbinary", "binary" ->
                    type + length(parameter.maxLength());
            case "nvarchar", "nchar" ->
                    type + length(parameter.maxLength() < 0 ? -1 : parameter.maxLength() / 2);
            case "decimal", "numeric" ->
                    type + "(" + safePrecision(parameter.precision()) + ","
                            + safeScale(parameter.scale(), parameter.precision()) + ")";
            case "datetime2", "datetimeoffset", "time" ->
                    type + "(" + Math.max(parameter.scale(), 0) + ")";
            default -> type;
        };
    }

    private int safePrecision(int precision) {
        return precision <= 0 ? 38 : precision;
    }

    private int safeScale(int scale, int precision) {
        return Math.max(0, Math.min(scale, safePrecision(precision)));
    }

    private String length(int value) {
        return value < 0 ? "(MAX)" : "(" + Math.max(value, 1) + ")";
    }

    private void validateInputParameters(ProcedureDefinition definition,
                                         Map<String, Object> input) {
        if (input == null || input.isEmpty()) return;

        Set<String> known = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        definition.parameters().forEach(parameter -> known.add(parameter.name()));

        input.keySet().stream()
                .filter(key -> !known.contains(key))
                .findFirst()
                .ifPresent(key -> {
                    throw new IllegalArgumentException("Parámetro desconocido: " + key);
                });
    }

    private RuntimeException mapException(Throwable error) {
        if (error instanceof StoredProcedureExecutionException exception) return exception;

        if (error instanceof R2dbcException exception) {
            return new StoredProcedureExecutionException(
                    exception.getErrorCode(), exception.getSqlState(),
                    exception.getMessage(), exception
            );
        }

        if (error instanceof IllegalArgumentException exception) return exception;
        if (error instanceof NoSuchElementException exception) return exception;

        String message = error.getMessage() == null
                ? error.getClass().getSimpleName()
                : error.getMessage();

        return new StoredProcedureExecutionException(0, null, message, error);
    }

    private <T> Mono<T> withConnection(ConnectionFactory connectionFactory,
                                       Function<Connection, Mono<T>> action) {
        return Mono.usingWhen(
                Mono.from(connectionFactory.create()),
                action,
                this::closeConnection,
                (connection, error) -> closeConnection(connection),
                this::closeConnection
        );
    }

    private Mono<Void> closeConnection(Connection connection) {
        return Mono.from(connection.close()).onErrorResume(error -> Mono.empty());
    }

    private String outputVariable(ProcedureParameter parameter) {
        return OUTPUT_VARIABLE_PREFIX + parameter.ordinal();
    }

    private String inputVariable(ProcedureParameter parameter) {
        return INPUT_VARIABLE_PREFIX + parameter.ordinal();
    }

    private String quotedParameter(String parameterName) {
        return "@" + stripAt(parameterName);
    }

    private String quotedIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            throw new IllegalArgumentException("El identificador SQL no puede estar vacío");
        }

        return "[" + identifier.replace("]", "]]") + "]";
    }

    private boolean isXml(ProcedureParameter parameter) {
        return "xml".equals(normalizedType(parameter));
    }

    private String normalizedType(ProcedureParameter parameter) {
        return parameter.sqlTypeName() == null
                ? ""
                : parameter.sqlTypeName().trim().toLowerCase(Locale.ROOT);
    }

    private static int number(Object value) {
        if (value == null) return 0;
        return value instanceof Number number
                ? number.intValue()
                : Integer.parseInt(value.toString());
    }

    private static boolean bool(Object value) {
        if (value == null) return false;
        if (value instanceof Boolean booleanValue) return booleanValue;
        return value instanceof Number number
                ? number.intValue() != 0
                : Boolean.parseBoolean(value.toString());
    }

    private static String stripAt(String name) {
        return name == null ? "" : name.replaceFirst("^@", "");
    }

    private static boolean containsIgnoreCase(Map<String, Object> map, String key) {
        return map != null
                && map.keySet().stream().anyMatch(candidate -> candidate.equalsIgnoreCase(key));
    }

    private static Object findIgnoreCase(Map<String, Object> map, String key) {
        if (map == null || map.isEmpty()) return null;

        return map.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(key))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    private sealed interface SegmentData {
        record RowData(Map<String, Object> row) implements SegmentData {}
        record UpdateData(int count) implements SegmentData {}
    }

    private record ResultData(List<Map<String, Object>> rows,
                              List<Integer> updateCounts) {}

    private record BindingValue(String marker,
                                Object value,
                                ProcedureParameter parameter,
                                boolean bindAsString) {}

    private record ExecutionSql(String sql,
                                List<BindingValue> bindings) {}
}
