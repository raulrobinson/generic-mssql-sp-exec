package com.raulbolivar.proxy.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.raulbolivar.proxy.domain.ProcedureParameter;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;

@Component
final class R2dbcTypeMapper {

    private final ObjectMapper mapper;

    R2dbcTypeMapper(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    Object convert(Object value, ProcedureParameter parameter) {
        if (value == null) return null;
        JsonNode node = mapper.valueToTree(value);
        if (node.isNull()) return null;

        return switch (parameter.sqlTypeName().toLowerCase(Locale.ROOT)) {
            case "tinyint", "smallint", "int" -> node.asInt();
            case "bigint" -> node.asLong();
            case "numeric", "decimal", "money", "smallmoney" -> new BigDecimal(node.asText());
            case "float" -> node.asDouble();
            case "real" -> node.floatValue();
            case "bit" -> node.asBoolean();
            case "date" -> LocalDate.parse(node.asText());
            case "time" -> LocalTime.parse(node.asText());
            case "datetime", "datetime2", "smalldatetime" -> LocalDateTime.parse(node.asText());
            case "datetimeoffset" -> OffsetDateTime.parse(node.asText());
            case "binary", "varbinary", "image", "rowversion", "timestamp" -> Base64.getDecoder().decode(node.asText());
            case "uniqueidentifier" -> UUID.fromString(node.asText());
            default -> node.isTextual() ? node.asText() : node.toString();
        };
    }

    Class<?> nullType(ProcedureParameter parameter) {
        return switch (parameter.sqlTypeName().toLowerCase(Locale.ROOT)) {
            case "tinyint", "smallint", "int" -> Integer.class;
            case "bigint" -> Long.class;
            case "numeric", "decimal", "money", "smallmoney" -> BigDecimal.class;
            case "float" -> Double.class;
            case "real" -> Float.class;
            case "bit" -> Boolean.class;
            case "date" -> LocalDate.class;
            case "time" -> LocalTime.class;
            case "datetime", "datetime2", "smalldatetime" -> LocalDateTime.class;
            case "datetimeoffset" -> OffsetDateTime.class;
            case "binary", "varbinary", "image", "rowversion", "timestamp" -> byte[].class;
            case "uniqueidentifier" -> UUID.class;
            default -> String.class;
        };
    }
}
