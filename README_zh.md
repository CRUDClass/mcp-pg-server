# mcp-pg-server

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[English](README.md)

基于 Spring Boot 和 Spring AI 的 PostgreSQL MCP（模型上下文协议）服务器，将数据库 CRUD 操作暴露为 AI 代理可用的 MCP 工具。

## 特性

- **完整 CRUD 工具** — Query、Insert、Update、Delete、Batch、DDL、ListTables、DescribeTable
- **读写支持** — 支持所有 DML 和 DDL 操作（非只读）
- **SQL 校验** — 内置基于 JSqlParser 的 SQL 注入防护
- **中英双语** — 通过 `TOOL_LOCALE` 环境变量切换工具描述语言（`zh` / `en`）
- **MCP STREAMABLE 传输** — 基于 HTTP 的 MCP 协议，端点 `/api/mcp`
- **Docker 支持** — 包含 Dockerfile 和 docker-compose.yml

## 快速开始

### 前置条件

- Java 21+
- Maven 3.9+
- PostgreSQL 14+
- Docker（可选）

### 安装

```bash
# 克隆仓库
git clone https://github.com/CRUDClass/mcp-pg-server.git
cd mcp-pg-server

# 配置环境变量
cp .env.example .env
# 编辑 .env 填入你的 PostgreSQL 连接信息

# 构建
mvn package -DskipTests

# 运行
java -jar target/pg-server-1.0.0.jar
```

### Docker

```bash
docker compose up -d --build
```

### Docker（STDIO 模式）

适用于通过标准输入/输出通信的 MCP 客户端（如 Claude Desktop）：

```bash
docker run -i --rm --init --pull=always \
  -e SPRING_PROFILES_ACTIVE=stdio \
  -e PG_HOST=your-host \
  -e PG_PORT=5432 \
  -e PG_DATABASE=your-db \
  -e PG_USERNAME=your-user \
  -e PG_PASSWORD=your-password \
  mcp-pg-server:latest
```

**MCP 客户端配置：**

```json
{
  "mcpServers": {
    "pg-server": {
      "command": "docker",
      "args": [
        "run", "-i", "--rm", "--init", "--pull=always",
        "-e", "SPRING_PROFILES_ACTIVE=stdio",
        "-e", "PG_HOST",
        "-e", "PG_PORT",
        "-e", "PG_DATABASE",
        "-e", "PG_USERNAME",
        "-e", "PG_PASSWORD",
        "mcp-pg-server:latest"
      ]
    }
  }
}
```

> `--pull=always` 确保每次拉取最新镜像，使用本地构建的镜像时可省略。

### MCP 客户端配置

在 MCP 客户端配置中添加：

```json
{
  "mcpServers": {
    "pg-server": {
      "url": "http://localhost:15432/api/mcp"
    }
  }
}
```

## MCP 工具

| 工具 | 描述 |
|------|------|
| `query` | 执行 SELECT 查询 |
| `insert` | 向表中插入数据 |
| `update` | 更新表中数据 |
| `delete` | 删除表中数据 |
| `batch` | 在事务中批量执行多条 SQL |
| `ddl` | 执行 DDL 语句（CREATE TABLE、DROP TABLE） |
| `listTables` | 列出所有数据表 |
| `describeTable` | 查看表的完整元数据 |

## 配置

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `SERVER_PORT` | `15432` | 服务端口 |
| `PG_HOST` | `localhost` | PostgreSQL 主机地址 |
| `PG_PORT` | `5432` | PostgreSQL 端口 |
| `PG_DATABASE` | `hot_topics` | 数据库名称 |
| `PG_USERNAME` | `root` | 数据库用户 |
| `PG_PASSWORD` | `123456` | 数据库密码 |
| `TOOL_LOCALE` | `zh` | 工具描述语言（`zh` 中文 / `en` 英文） |
| `SPRING_PROFILES_ACTIVE` | (未设置) | 设为 `stdio` 启用 STDIO 传输模式 |

## 贡献

请参阅 [CONTRIBUTING.md](CONTRIBUTING.md) 了解分支策略和提交流程。

## 许可证

MIT &copy; [CRUDClass](https://github.com/CRUDClass)
