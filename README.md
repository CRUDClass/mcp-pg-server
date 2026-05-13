# mcp-pg-server

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[中文说明](README_zh.md)

A PostgreSQL MCP (Model Context Protocol) server built with Spring Boot and Spring AI, exposing database CRUD operations as MCP tools for AI agents.

## Features

- **Full CRUD tools** — Query, Insert, Update, Delete, Batch, DDL, ListTables, DescribeTable
- **Read/write support** — Unlike read-only MCP servers, this supports all DML and DDL operations
- **SQL validation** — Built-in SQL injection prevention via JSqlParser
- **Bilingual i18n** — Tool descriptions switch between Chinese and English via `TOOL_LOCALE` env var
- **MCP STREAMABLE transport** — HTTP-based MCP protocol at `/api/mcp`
- **Docker-ready** — Dockerfile and docker-compose.yml included

## Quick Start

### Prerequisites

- Java 21+
- Maven 3.9+
- PostgreSQL 14+
- Docker (optional)

### Setup

```bash
# Clone
git clone https://github.com/CRUDClass/mcp-pg-server.git
cd mcp-pg-server

# Configure environment
cp .env.example .env
# Edit .env with your PostgreSQL connection

# Build
mvn package -DskipTests

# Run
java -jar target/pg-server-1.0.0.jar
```

### Docker

```bash
docker compose up -d --build
```

### MCP Client Configuration

Add to your MCP client's configuration:

```json
{
  "mcpServers": {
    "pg-server": {
      "url": "http://localhost:15432/api/mcp"
    }
  }
}
```

## MCP Tools

| Tool | Description |
|------|-------------|
| `query` | Execute SELECT queries |
| `insert` | Insert rows into a table |
| `update` | Update rows in a table |
| `delete` | Delete rows from a table |
| `batch` | Execute multiple SQL statements in a transaction |
| `ddl` | Execute DDL statements (CREATE TABLE, DROP TABLE) |
| `listTables` | List all tables in the database |
| `describeTable` | Describe a table's schema |

## Configuration

| Variable | Default | Description |
|----------|---------|-------------|
| `SERVER_PORT` | `15432` | Server port |
| `PG_HOST` | `localhost` | PostgreSQL host |
| `PG_PORT` | `5432` | PostgreSQL port |
| `PG_DATABASE` | `hot_topics` | Database name |
| `PG_USERNAME` | `root` | Database user |
| `PG_PASSWORD` | `123456` | Database password |
| `TOOL_LOCALE` | `zh` | Tool description language (`zh` or `en`) |

## License

MIT &copy; [CRUDClass](https://github.com/CRUDClass)
