# AGENTS.md

## Build & Run

```bash
# Build + run tests (tests pass without a PostgreSQL connection)
mvn package

# Build skipping tests
mvn package -DskipTests

# Run all-in-one (build JAR → rebuild Docker image → start containers)
./build-local.sh

# Run locally (requires PostgreSQL)
mvn spring-boot:run

# Run JAR
java -jar target/pg-server-1.0.0.jar

# Run in STDIO mode (for MCP client integration) — Docker
docker run -i --rm --init --pull=always \
  -e SPRING_PROFILES_ACTIVE=stdio \
  -e PG_HOST=your-host -e PG_DATABASE=your-db \
  -e PG_USERNAME=your-user -e PG_PASSWORD=your-password \
  mcp-pg-server:latest

# Run in STDIO mode — local JAR
SPRING_PROFILES_ACTIVE=stdio java -jar target/pg-server-1.0.0.jar
```

## Run a single test

```bash
mvn test -Dtest=SqlValidatorTest -pl .
```

Tests in `SqlValidatorTest` pass a `null` JdbcTemplate to `SqlValidator`, so PostgreSQL is **not** required for tests. The `EXPLAIN` handshake layer is silently skipped when no DataSource is available.

## Environment

Copy `.env.example` to `.env` and configure PostgreSQL connection. All `application.yml` values reference env vars with defaults:

| Variable | Default | Description |
|----------|---------|-------------|
| `SERVER_PORT` | 15432 | Server port |
| `PG_HOST` | localhost | PostgreSQL host |
| `PG_PORT` | 5432 | PostgreSQL port |
| `PG_DATABASE` | hot_topics | Database name |
| `PG_USERNAME` | root | Database user |
| `PG_PASSWORD` | 123456 | Database password |
| `TOOL_LOCALE` | zh | Tool description language (`zh` or `en`) |

## Architecture

- **Framework**: Spring Boot 3.5.14, Spring AI 1.1.2, Java 21
- **Web container**: Undertow (Tomcat excluded — macOS `SO_LINGER` compatibility)
- **MCP protocol**: STREAMABLE (HTTP), endpoint at `/api/mcp`
- **Package**: `com.crudclass.mcpserver.pg`

### 8 MCP tools (all in `tool/` package)

| Tool | Class | Type | Confirm? |
|------|-------|------|----------|
| `executeQuery` | `QueryTool` | Read-only SELECT/WITH | No |
| `executeInsert` | `InsertTool` | INSERT | Yes (two-phase) |
| `executeUpdate` | `UpdateTool` | UPDATE | Yes (two-phase) |
| `executeDelete` | `DeleteTool` | DELETE | Yes (two-phase) |
| `executeDdl` | `DdlTool` | CREATE TABLE / DROP TABLE | Yes (two-phase) |
| `executeBatch` | `BatchTool` | Multi-statement transaction | Yes (two-phase) |
| `listTables` | `ListTablesTool` | Read schema metadata | No |
| `describeTable` | `DescribeTableTool` | Read table metadata | No |

All tools are explicitly registered in `McpPgServerApplication.toolCallbackProvider()` — Spring AI 1.1.2 does not auto-detect `@Tool` beans in WebMVC server config.

### Confirm pattern (write tools)

Write tools (Insert, Update, Delete, DDL, Batch) use a **two-phase preview/confirm** pattern:
1. **First call** (without `confirm` or `confirm=false`): validates SQL, returns a preview with `actionRequired: "confirm"`. Nothing is executed.
2. **Second call** (same SQL, `confirm=true`): actually executes the statement.

### Validation pipeline (`SqlValidator`)

3-layer validation before any SQL reaches the database:
1. **JSqlParser AST** — detects statement type, blocks multi-statements, reject-forbidden ops (TRUNCATE, ALTER, GRANT, EXECUTE, SET, etc.), checks for writable CTEs
2. **Regex** — catches PostgreSQL `FOR UPDATE / FOR SHARE` clauses JSqlParser 5.0 doesn't recognize
3. **EXPLAIN handshake** — sends `EXPLAIN <sql>` to PG; non-fatal (logged as warning if it fails)

### Idempotency (`SqlExecutor`)

Non-SELECT statements are cached by SHA-256 hash (Caffeine LRU, 5min TTL, max 1000 entries). Replayed writes return the cached result with an `IDEMPOTENT_REPLAY` audit log entry.

### Service layer

- `IPgService` — interface
- `PgServiceImpl` — single implementation; delegates to `SqlValidator` → `SqlExecutor`
- `SqlExecutor` — JDBC execution with 30s timeout, audit logging, idempotency
- `SqlValidator` — JSqlParser-based validation (no regex-only bypass)

## Conventions

- **No pre-commit hooks** or CI/CD configured.
- **Sonar**: `mvnd sonar:sonar -s <your-maven-settings> -Dmaven.repo.local=<your-repo> -Dsonar.projectKey=mcp-pg-server -Dsonar.host.url=http://172.28.0.9:9000 -Dsonar.token=<token>`
- **Docker**: image exposes port 15432. `docker-compose.yml` reads `.env` for env vars.
- **`.gitignore`** ignores `AGENTS.md`, `opencode.json`, and `docs/superpowers/` — these files should not be accidentally committed.
