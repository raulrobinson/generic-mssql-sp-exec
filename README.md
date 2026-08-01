# Generic MSSQL SP Exec

> A robust, production-ready proxy service for executing SQL Server stored procedures with security validation, reactive processing, and comprehensive REST APIs.

[![Java](https://img.shields.io/badge/Java-21-ED8936?style=flat-square&logo=openjdk)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.16-6DB33F?style=flat-square&logo=spring-boot)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-MIT-blue?style=flat-square)](LICENSE)

## 📋 Overview

**Generic MSSQL SP Exec** is a high-performance, asynchronous gateway service designed to provide secure, standardized access to SQL Server stored procedures. Built with Spring Boot WebFlux and R2DBC, it enables reactive, non-blocking execution of database operations with built-in security controls, connection pooling, and comprehensive error handling.

### Key Features

- 🚀 **Reactive Architecture** - Non-blocking, asynchronous execution using Project Reactor
- 🔒 **Security-First Design** - Whitelist-based stored procedure filtering with SQL injection prevention
- 📊 **Connection Pooling** - Configurable R2DBC connection pool with adaptive sizing
- 📡 **RESTful API** - Clean, intuitive endpoints for executing, describing, and listing procedures
- 📖 **OpenAPI Documentation** - Integrated Swagger UI for API exploration
- 🔧 **Flexible Configuration** - Environment-based configuration for multi-environment deployment
- 📦 **Docker Ready** - Pre-configured Dockerfile for containerized deployment
- ✅ **Type Safety** - Strong typing with Lombok and record-based domain models

## 🏗️ Architecture

```
┌─────────────────────────────────────────────┐
│           REST API Layer (WebFlux)          │
│     StoredProcedureHandler & Router         │
├─────────────────────────────────────────────┤
│        Application Service Layer            │
│      StoredProcedureService (Validation)    │
├─────────────────────────────────────────────┤
│           Gateway Pattern Layer             │
│   StoredProcedureGateway (R2DBC Adapter)    │
├─────────────────────────────────────────────┤
│         R2DBC Connection Pool               │
│            (MSSQL Driver)                   │
├─────────────────────────────────────────────┤
│         SQL Server Database                 │
└─────────────────────────────────────────────┘
```

### Layered Architecture

- **API Layer** - REST handlers and routing configuration
- **Application Layer** - Business logic and security validation
- **Infrastructure Layer** - R2DBC adapter, type mapping, connection management
- **Domain Layer** - Core business objects and contracts

## 🚀 Quick Start

### Prerequisites

- **Java 21** or later
- **SQL Server 2019+** or Azure SQL Database
- **Gradle 8.0+** (included via Gradle Wrapper)
- **Docker** (optional, for containerized deployment)

### Installation

1. **Clone the repository**
   ```bash
   git clone https://github.com/raulrobinson/generic-mssql-sp-exec.git
   cd generic-mssql-sp-exec
   ```

2. **Configure environment variables**
   ```bash
   cp .env.sample .env
   ```
   Update the `.env` file with your database credentials and configuration.

3. **Build the application**
   ```bash
   ./gradlew build
   ```

4. **Run the application**
   ```bash
   ./gradlew bootRun
   ```

   The service will start on `http://localhost:8080`

## 📝 Configuration

All configuration is managed through environment variables. Create a `.env` file or set environment variables:

### Database Configuration
```env
# SQL Server R2DBC Connection URL
PERSISTENCE_R2DBC_URL=r2dbc:mssql://localhost:1433/databaseName

# Database credentials
PERSISTENCE_USER=username
PERSISTENCE_PASS=password
```

### Connection Pool Settings
```env
# Default schema for queries
DB_SCHEMA=dbo

# Connection pool configuration
DB_POOL_INITIAL_SIZE=1
DB_POOL_MAX_SIZE=10
DB_POOL_MAX_IDLE_TIME=10m
DB_POOL_MAX_ACQUIRE_TIME=15s
```

### Stored Procedure Settings
```env
# Query execution timeout
SP_TIMEOUT=30s

# Maximum rows to return per query
SP_MAX_ROWS=1000
```

### Logging Configuration
```env
# R2DBC driver logging level
R2DBC_LOG_LEVEL=INFO

# R2DBC connection pool logging level
R2DBC_POOL_LOG_LEVEL=INFO
```

### Spring Boot Configuration
Additional Spring Boot properties can be added to `application.yml` or `application.properties`.

## 🔌 API Endpoints

The service exposes three main endpoints for stored procedure management:

### 1. Execute Stored Procedure
Execute a stored procedure with parameters.

**Endpoint:** `POST /api/v1/stored-procedures/execute`

**Request Body:**
```json
{
  "schema": "dbo",
  "procedure": "sp_GetUserById",
  "parameters": {
    "userId": 123,
    "includeDetails": true
  }
}
```

**Response:**
```json
{
  "procedureName": "sp_GetUserById",
  "returnCode": 0,
  "rowsAffected": 1,
  "data": [
    {
      "userId": 123,
      "userName": "john.doe",
      "email": "john@example.com"
    }
  ]
}
```

**Status Codes:**
- `200 OK` - Procedure executed successfully
- `400 Bad Request` - Invalid request body or parameters
- `403 Forbidden` - Procedure not in whitelist
- `500 Internal Server Error` - Database execution error

---

### 2. Describe Stored Procedure
Retrieve metadata about a stored procedure including its parameters and types.

**Endpoint:** `GET /api/v1/stored-procedures/{procedure}`

**Query Parameters:**
- `schema` (optional) - Schema name (defaults to `dbo`)

**Example Request:**
```bash
GET /api/v1/stored-procedures/sp_GetUserById?schema=dbo
```

**Response:**
```json
{
  "procedureName": "sp_GetUserById",
  "schema": "dbo",
  "parameters": [
    {
      "name": "userId",
      "type": "int",
      "nullable": false,
      "description": "User identifier"
    },
    {
      "name": "includeDetails",
      "type": "bit",
      "nullable": true,
      "defaultValue": "0"
    }
  ]
}
```

**Status Codes:**
- `200 OK` - Procedure description retrieved
- `404 Not Found` - Procedure does not exist
- `403 Forbidden` - Procedure not in whitelist

---

### 3. List Allowed Procedures
Retrieve the list of all procedures available for execution.

**Endpoint:** `GET /api/v1/stored-procedures`

**Response:**
```json
{
  "allowedProcedures": [
    "dbo.sp_GetUserById",
    "dbo.sp_CreateUser",
    "dbo.sp_UpdateUser",
    "dbo.sp_DeleteUser"
  ],
  "count": 4
}
```

**Status Codes:**
- `200 OK` - List retrieved successfully

---

## 🔐 Security Features

### SQL Injection Prevention
- **Parameterized Queries** - All parameters are handled through R2DBC prepared statements
- **SQL Identifier Validation** - Schema and procedure names are validated against a strict pattern: `^[A-Za-z_]\w*$`
- **Whitelist Enforcement** - Only procedures explicitly listed in the `ALLOWED_PROCEDURES` configuration can be executed

### Authorization
- **Stored Procedure Whitelist** - Define allowed procedures in configuration:
  ```properties
  app.stored-procedures.allowed=dbo.sp_GetUserById,dbo.sp_CreateUser,dbo.sp_UpdateUser
  ```
- **Schema Isolation** - Operations are scoped to specific schemas
- **Input Validation** - All request parameters are validated before execution

## 🐳 Docker Deployment

### Build Docker Image
```bash
./gradlew build
docker build -t generic-mssql-sp-exec:latest .
```

### Run Container
```bash
docker run -d \
  --name mssql-sp-exec \
  -p 8080:8080 \
  -e PERSISTENCE_R2DBC_URL=r2dbc:mssql://db-server:1433/mydb \
  -e PERSISTENCE_USER=sa \
  -e PERSISTENCE_PASS=YourPassword123! \
  -e DB_POOL_MAX_SIZE=20 \
  generic-mssql-sp-exec:latest
```

### Docker Compose Example
```yaml
version: '3.8'

services:
  mssql:
    image: mcr.microsoft.com/mssql/server:2022-latest
    environment:
      SA_PASSWORD: YourPassword123!
      ACCEPT_EULA: 'Y'
    ports:
      - "1433:1433"

  sp-executor:
    build: .
    depends_on:
      - mssql
    ports:
      - "8080:8080"
    environment:
      PERSISTENCE_R2DBC_URL: r2dbc:mssql://mssql:1433/master
      PERSISTENCE_USER: sa
      PERSISTENCE_PASS: YourPassword123!
      DB_POOL_MAX_SIZE: 20
```

## 📚 API Documentation

Once the application is running, access the interactive API documentation:

- **Swagger UI:** `http://localhost:8080/swagger-ui.html`
- **OpenAPI JSON:** `http://localhost:8080/v3/api-docs`

## 🧪 Testing

Run the test suite:

```bash
./gradlew test
```

Run tests with coverage:

```bash
./gradlew test jacocoTestReport
```

## 📦 Build & Packaging

### Build JAR
```bash
./gradlew build
# Output: build/libs/generic-mssql-sp-exec-*.jar
```

### Build without tests
```bash
./gradlew build -x test
```

### Clean build
```bash
./gradlew clean build
```

## 🔍 Monitoring & Logging

### Logging Configuration
Logs are output to console by default. Configure in `application.yml`:

```yaml
logging:
  level:
    com.raulbolivar.proxy: DEBUG
    io.r2dbc: INFO
    org.springframework.web: INFO
```

### Health Check
The service includes Spring Boot Actuator endpoints:

```bash
curl http://localhost:8080/actuator/health
```

## 🐛 Troubleshooting

### Connection Issues
**Problem:** `Unable to connect to database`

**Solutions:**
1. Verify SQL Server is running and accessible
2. Check firewall rules and network connectivity
3. Verify credentials in `.env` file
4. Check R2DBC connection URL format

### Procedure Not Found
**Problem:** `403 Forbidden - Procedure not permitted`

**Solutions:**
1. Verify procedure exists in database
2. Check if procedure is in the whitelist configuration
3. Verify schema name is correct
4. Ensure procedure name matches exactly (case-sensitive in some schemas)

### Connection Pool Exhaustion
**Problem:** `Timeout acquiring connection from pool`

**Solutions:**
1. Increase `DB_POOL_MAX_SIZE` in configuration
2. Reduce `SP_TIMEOUT` to fail faster on slow queries
3. Check for long-running queries in SQL Server
4. Monitor connection pool metrics

### Performance Issues
**Solutions:**
1. Increase `DB_POOL_SIZE` for better throughput
2. Add database indexes on frequently queried columns
3. Reduce `SP_MAX_ROWS` to limit memory usage
4. Enable R2DBC query logging to identify slow queries

## 🤝 Contributing

Contributions are welcome! Please follow these guidelines:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 👤 Author

**Raúl Bolívar**
- GitHub: [@raulrobinson](https://github.com/raulrobinson)

## 💬 Support

For issues, questions, or suggestions, please [open an issue](https://github.com/raulrobinson/generic-mssql-sp-exec/issues) on GitHub.

## 🎯 Roadmap

- [ ] Query result caching layer
- [ ] Metrics and prometheus integration
- [ ] Stored procedure execution history
- [ ] GraphQL query interface
- [ ] Database change notifications (SSE)
- [ ] Request/response middleware pipeline
- [ ] Performance benchmarking suite

## 📊 Technology Stack

- **Framework:** Spring Boot 3.5.16
- **Reactive Stack:** Project Reactor, Spring WebFlux
- **Database:** R2DBC MSSQL Driver
- **Validation:** Jakarta Validation
- **Documentation:** SpringDoc OpenAPI (Swagger)
- **Build Tool:** Gradle
- **Java Version:** 21 LTS
- **Container:** Docker with Eclipse Temurin Base Image

## ⚡ Performance Characteristics

- **Request Processing:** Non-blocking, asynchronous
- **Connection Pool:** Configurable reactive R2DBC pool
- **Data Type Mapping:** Automatic SQL Server ↔ Java type conversion
- **Memory Footprint:** Optimized with record-based domain models
- **Throughput:** Typically 1,000-5,000 req/s (depends on query complexity and DB performance)

## 🔗 Related Resources

- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [Spring WebFlux](https://spring.io/reactive)
- [R2DBC MSSQL Driver](https://github.com/r2dbc/r2dbc-mssql)
- [Project Reactor](https://projectreactor.io/)
