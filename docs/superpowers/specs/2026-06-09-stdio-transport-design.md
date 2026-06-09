# MCP PostgreSQL Server — STDIO Transport & Docker Run 支持

**日期**: 2026-06-09  
**状态**: Approved  
**作者**: Claude

---

## 1. 目标

为 mcp-pg-server 添加 STDIO 传输支持，使其可以通过 `docker run -i` 方式启动，同时保留现有的 HTTP/STREAMABLE 传输方式。

## 2. 动机

- MCP 客户端（如 Claude Desktop）支持通过 STDIO 协议与服务器通信
- 现有的 HTTP 模式需要通过 `docker compose` 或 `docker run -d -p` 暴露端口
- STDIO 模式更轻量，适合 MCP 客户端的本地集成

## 3. 现状

### 3.1 当前架构

```
┌─────────────────────┐
│  Spring Boot Web    │
│  (Undertow)         │
│  port 15432         │
│                     │
│  MCP STREAMABLE     │
│  HTTP /api/mcp      │
└─────────────────────┘
        ↕ HTTP
┌─────────────────────┐
│  MCP Client         │
│  (url: localhost:   │
│   15432/api/mcp)    │
└─────────────────────┘
```

### 3.2 当前配置方式

- 数据库连接参数（PG_HOST, PG_PORT, PG_DATABASE, PG_USERNAME, PG_PASSWORD）已通过 `${VAR:default}` 形式支持环境变量注入
- Docker Compose 通过 `env_file: .env` 加载环境变量
- 镜像暴露端口 15432

### 3.3 核心技术

- Spring Boot 3.5.14 + Spring AI 1.1.2
- `spring-ai-starter-mcp-server-webmvc` starter
- 内置的 STDIO 传输支持（`spring.ai.mcp.server.stdio=true`）

## 4. 设计

### 4.1 目标架构

```
┌─ HTTP 模式（现有，不变）───┐
│  docker compose up         │
│  ┌──────────────────────┐  │
│  │  Spring Boot Web     │  │
│  │  MCP STREAMABLE HTTP │  │
│  │  localhost:15432     │  │
│  └──────────────────────┘  │
└────────────────────────────┘

┌─ STDIO 模式（新增）─────────┐
│  docker run -i              │
│  ┌──────────────────────┐  │
│  │  Spring Boot         │  │
│  │  (web=none)          │  │
│  │  MCP STDIO           │  │
│  │  stdin → stdout      │  │
│  └──────────────────────┘  │
│           ↕ stdin/stdout   │
│  ┌──────────────────────┐  │
│  │  MCP Client          │  │
│  │  (docker command)    │  │
│  └──────────────────────┘  │
└────────────────────────────┘
```

### 4.2 设计方案

通过 **Spring Profile** 切换两种模式：

| 模式 | Profile | 传输方式 | Web 服务器 |
|------|---------|----------|-----------|
| HTTP（默认） | 无（default） | STREAMABLE HTTP | Undertow :15432 |
| STDIO | `stdio` | STDIO | 不启动 |

#### 4.2.1 新增文件: `application-stdio.yml`

```yaml
spring:
  ai:
    mcp:
      server:
        stdio: true        # 启用 STDIO 传输
  main:
    web-application-type: none   # 关闭嵌入式 Web 服务器
```

其他配置（数据库连接、MCP 工具等）继承自 `application.yml`。

#### 4.2.2 STDIO 传输原理

Spring AI 1.1.2 的 `McpServerAutoConfiguration` 包含以下机制：

- 当 `spring.ai.mcp.server.stdio=true` 时，`StdioServerTransportProvider` 被激活
- HTTP 传输（SSE / Streamable HTTP）的自动配置带有 `McpServerStdioDisabledCondition` 条件，当 STDIO 启用时自动跳过
- 两者自动**互斥**，无需手动排除

#### 4.2.3 Docker Compose 保留不变

`docker-compose.yml` 不修改，继续用于 HTTP 模式的容器编排。

#### 4.2.4 Dockerfile 调整

**当前:**
```dockerfile
ENTRYPOINT ["java", "-jar", "/app/mcp-pg-server.jar"]
```

**建议改为入口脚本方式（可选）:**
若需支持 `MCP_MODE=stdio` 环境变量方式，可添加入口脚本。但目前推荐直接通过 `SPRING_PROFILES_ACTIVE=stdio` 环境变量控制，Dockerfile 可保持不变。

#### 4.2.5 Java 源码

**零改动。** 所有变更均为配置和文档级别。

### 4.3 启动方式

#### HTTP 模式（不变）

```bash
# 方式一: Docker Compose
docker compose up -d --build

# 方式二: Docker Run
docker run -d --init --pull=always \
  -p 15432:15432 \
  --name mcp-pg \
  -e PG_HOST=... -e PG_DATABASE=... \
  -e PG_USERNAME=... -e PG_PASSWORD=... \
  mcp-pg-server:latest
```

**MCP 客户端配置:**
```json
{
  "mcpServers": {
    "pg-server": {
      "url": "http://localhost:15432/api/mcp"
    }
  }
}
```

#### STDIO 模式（新增）

```bash
docker run -i --rm --init --pull=always \
  -e SPRING_PROFILES_ACTIVE=stdio \
  -e PG_HOST=... -e PG_DATABASE=... \
  -e PG_USERNAME=... -e PG_PASSWORD=... \
  mcp-pg-server:latest
```

**MCP 客户端配置:**
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

### 4.4 环境变量一览

| 变量 | HTTP 默认值 | STDIO 默认值 | 说明 |
|------|-----------|-------------|------|
| `SERVER_PORT` | 15432 | 不适用 | HTTP 端口（STDIO 模式忽略） |
| `PG_HOST` | localhost | localhost | PostgreSQL 主机 |
| `PG_PORT` | 5432 | 5432 | PostgreSQL 端口 |
| `PG_DATABASE` | hot_topics | hot_topics | 数据库名称 |
| `PG_USERNAME` | root | root | 数据库用户 |
| `PG_PASSWORD` | 123456 | 123456 | 数据库密码 |
| `TOOL_LOCALE` | zh | zh | 工具描述语言 |
| `SPRING_PROFILES_ACTIVE` | (未设置) | `stdio` | 激活 STDIO 模式 |

### 4.5 错误处理与边界情况

| 场景 | 行为 |
|------|------|
| 未设置 `SPRING_PROFILES_ACTIVE` | 默认 HTTP 模式，向后兼容 |
| STDIO 模式下同时设置了 `SERVER_PORT` | 端口配置被忽略（web=none） |
| 数据库连接失败 | 启动时 Application 报错退出，同 HTTP 模式 |
| 缺少必要 PG 环境变量 | Spring Boot 使用默认值启动，运行时报错 |
| `--pull=always` 拉取失败 | Docker 报错退出，本地构建时需要省略此标志 |

### 4.6 测试策略

| 测试项 | 方式 |
|--------|------|
| STDIO Profile 加载 | 验证 `application-stdio.yml` 属性被正确加载 |
| 功能回归 | 现有 HTTP 模式测试全部通过 |
| 构建验证 | `mvn package -DskipTests` 成功 |

## 5. 不纳入范围

- 不修改任何 Java 源码
- 不修改 `pom.xml`（现有依赖已覆盖）
- 不新增 Docker 入口脚本
- 不修改 `.env` / `.env.example`
- 不留存 build log 文件

## 6. 后续工作（Implementation Plan）

1. 新建 `src/main/resources/application-stdio.yml`
2. 更新 `README.md` 和 `README_zh.md` 添加 STDIO 模式文档
3. 更新 `AGENTS.md` 添加 STDIO 模式启动命令
4. 构建验证: `mvn package -DskipTests`
5. 提交 commit

---

*文档结束*
