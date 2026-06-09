# MCP 工具国际化（中英双语）设计

## 目标

根据环境变量 `TOOL_LOCALE`（已有，默认 `zh`）控制：
1. MCP 工具描述和参数描述切换中/英文
2. 运行时消息（预览、成功、错误）切换中/英文
3. 异常消息按 locale 返回单语而非双语连体

## 方案

**LocaleAware ToolCallback 包装器**（方案 B）：保留 `@Tool` 注解类不变，创建包装器在运行时替换 ToolDefinition。

## 改动文件清单

| 文件 | 改动 |
|------|------|
| `config/ToolMessages.java` | 扩展：新增 ~30 个 getter（工具描述、参数描述、运行时消息） |
| `tool/LocaleAwareToolCallbackProvider.java` | **新增**：核心包装 provider |
| `enums/McpErrorCode.java` | 改造：zh/en 分离，新增 `getMessage(locale)` |
| `error/McpBusinessException.java` | 改造：构造函数增加 locale 参数 |
| `error/ExceptionMapper.java` | 改造：注入 ToolMessages，传 locale |
| `service/SqlExecutor.java` | 改造：注入 ToolMessages，替换硬编码中文 |
| `service/impl/PgServiceImpl.java` | 改造：注入 ToolMessages，替换硬编码中文 |
| `McpPgServerApplication.java` | 改造：注入 ToolMessages，替换 provider 注册 |
| 8 个 tool 类 | **不变** |
| `application.yml` | **不变** |

## 1. ToolMessages 扩展

### 1.1 新增工具描述方法

```java
String listTablesDescription()    // "列出 public schema 下所有表名及注释" / "List all tables and comments..."
String describeTableDescription() // "查询 public schema 下某张表的完整元数据..." / "Describe table metadata..."
```

### 1.2 新增参数描述方法

```java
String insertSqlParamDesc()   // "PostgreSQL INSERT 语句" / "PostgreSQL INSERT statement"
String updateSqlParamDesc()   // 同上
String deleteSqlParamDesc()   // 同上
String ddlSqlParamDesc()      // "PostgreSQL CREATE TABLE 或 DROP TABLE 语句" / ...
String tableNameParamDesc()   // "表名" / "Table name"
```

### 1.3 新增运行时消息方法

```java
String previewMessage()
String batchPreviewMessage(int count)
String selectSuccess(int rows)
String insertSuccess(int affected)
String updateSuccess(int affected)
String deleteSuccess(int affected)
String createSuccess()
String dropSuccess()
String unsupportedType()
String ddlOnlySupport()
String tableNameNotEmpty()
String illegalTableName(String name)
```

## 2. LocaleAwareToolCallbackProvider（新增类）

```
LocaleAwareToolCallbackProvider implements ToolCallbackProvider
├── 字段: ToolMessages, MethodToolCallbackProvider(delegate)
├── 构造: 接收所有 8 个工具 bean + ToolMessages
│         delegate = MethodToolCallbackProvider.builder().toolObjects(...).build()
├── getToolCallbacks() → ToolCallback[]
│         delegate.getToolCallbacks() 后逐个 wrap()
├── wrap(ToolCallback) → ToolCallback
│         匿名实现:
│           getToolDefinition() → buildLocalizedDef(original.getToolDefinition())
│           call(input) → original.call(input)
├── buildLocalizedDef(ToolDefinition) → ToolDefinition
│         ToolDefinition.builder()
│           .name(original.name())
│           .description(descriptionFor(original.name()))
│           .inputSchema(buildSchema(original.name()))
│           .build()
├── descriptionFor(toolName) → String (switch 8 条分支 → ToolMessages)
├── buildSchema(toolName) → String
│         SchemaBuilder.build(paramsMap.get(toolName), messages)
│         参数描述通过 toolName 分发给 ToolMessages 对应方法
└── paramsMap: static final Map<String, List<ParamDef>>
     executeQuery    → [("sql",string,true), ("limit",integer,false), ("offset",integer,false)]
     executeInsert   → [("sql",string,true), ("confirm",boolean,false)]
     executeUpdate   → [("sql",string,true), ("confirm",boolean,false)]
     executeDelete   → [("sql",string,true), ("confirm",boolean,false)]
     executeDdl      → [("sql",string,true), ("confirm",boolean,false)]
     executeBatch    → [("sqls",array,true), ("confirm",boolean,false)]
     listTables      → []
     describeTable   → [("tableName",string,true)]
```

### ParamDef 与 SchemaBuilder

```java
record ParamDef(String name, String type, boolean required) {}
```

`SchemaBuilder.buildSchema(List<ParamDef>, String toolName, ToolMessages)` 生成标准 JSON Schema 字符串：

```json
{
  "type": "object",
  "properties": {
    "sql": { "type": "string", "description": "<ToolMessages locale-aware>" },
    "limit": { "type": "integer", "description": "<ToolMessages locale-aware>" }
  },
  "required": ["sql"]
}
```

`call(input)` 委托给原始 `MethodToolCallback`，参数反序列化不受影响。

## 3. McpErrorCode 改造

**当前：** 双语连体 `"SQL 不能为空 / SQL cannot be empty"`

**改为：**

```java
public enum McpErrorCode {
    SQL_EMPTY("SQL 不能为空", "SQL cannot be empty"),
    SQL_PARSE_ERROR("SQL 解析失败", "SQL parse error"),
    MULTI_STATEMENT("不允许执行多条 SQL", "Multiple statements not allowed"),
    FORBIDDEN_OPERATION("禁止的 SQL 操作", "Forbidden SQL operation"),
    TYPE_MISMATCH("SQL 类型不匹配", "SQL type mismatch"),
    WRITABLE_CTE("禁止可写 CTE", "Writable CTE not allowed"),
    QUERY_TIMEOUT("查询超时", "Query timeout"),
    EXECUTION_FAILED("SQL 执行失败", "SQL execution failed"),
    IDEMPOTENCY_CONFLICT("幂等冲突: 相同SQL已在执行中", "Idempotency conflict"),
    VALIDATION_FAILED("SQL 校验失败", "SQL validation failed");

    private final String zhMessage;
    private final String enMessage;

    McpErrorCode(String zh, String en) {
        this.zhMessage = zh;
        this.enMessage = en;
    }

    public String getMessage(String locale) {
        return "en".equalsIgnoreCase(locale) ? enMessage : zhMessage;
    }
}
```

## 4. McpBusinessException 改造

```java
public class McpBusinessException extends RuntimeException {
    private final McpErrorCode errorCode;
    private final String locale;

    // 无 locale 构造（兼容旧代码，默认 zh）
    public McpBusinessException(McpErrorCode errorCode) {
        super(errorCode.getMessage("zh"));
        this.errorCode = errorCode;
        this.locale = "zh";
    }

    public McpBusinessException(McpErrorCode errorCode, String detail, String locale) {
        super(errorCode.getMessage(locale) + ": " + detail);
        this.errorCode = errorCode;
        this.locale = locale;
    }
}
```

## 5. SqlExecutor 改造

注入 `ToolMessages`，替换以下硬编码：

| 原硬编码 | 替换为 |
|---------|--------|
| `"预览模式，SQL 校验通过，未实际执行..."` | `messages.previewMessage()` |
| `"查询成功，返回 " + rows.size() + " 行"` | `messages.selectSuccess(rows.size())` |
| `"插入成功，影响 " + affected + " 行"` | `messages.insertSuccess(affected)` |
| `"更新成功，影响 " + affected + " 行"` | `messages.updateSuccess(affected)` |
| `"删除成功，影响 " + affected + " 行"` | `messages.deleteSuccess(affected)` |
| `"创建成功"` | `messages.createSuccess()` |
| `"删除成功"` | `messages.dropSuccess()` |
| `"Unsupported SQL type: " + sqlType` | `messages.unsupportedType() + ": " + sqlType` |

## 6. PgServiceImpl 改造

注入 `ToolMessages`，替换：

| 原硬编码 | 替换为 |
|---------|--------|
| `"预览模式，共 X 条 SQL 校验通过..."` | `messages.batchPreviewMessage(sqls.size())` |
| `"仅支持 CREATE TABLE 和 DROP TABLE"` | `messages.ddlOnlySupport()` |
| `"tableName 不能为空"` | `messages.tableNameNotEmpty()` |
| `"非法表名: " + tableName` | `messages.illegalTableName(tableName)` |
| `"不支持的 SQL 类型"` | `messages.unsupportedType()` |

## 7. ExceptionMapper 改造

注入 `ToolMessages`，`handleMcpBusiness` 中 `body.put("message", ...)` 改为使用 `ex.getMessage()`（已是 locale 格式）。

`handleUnknown` 中 `"Internal error: "` 改为 `messages.isEnglish() ? "Internal error: " : "内部错误: "`。

## 8. McpPgServerApplication 改造

```java
@Bean
public ToolCallbackProvider toolCallbackProvider(
        ToolMessages messages,
        QueryTool queryTool, ...) {
    return new LocaleAwareToolCallbackProvider(messages,
            queryTool, insertTool, updateTool, deleteTool,
            ddlTool, batchTool, listTablesTool, describeTableTool);
}
```

## 9. 构建验证

```bash
mvn package          # 全量编译 + 测试
mvn test -Dtest=SqlValidatorTest -pl .   # 现有测试不受影响
TOOL_LOCALE=en mvn spring-boot:run       # 本地验证英文模式
```

## 10. 不改动项

- 8 个 `tool/*Tool.java` — 注解保持原样
- `application.yml` — `TOOL_LOCALE` 变量已存在
- `SqlValidator.java` — 验证逻辑不变
- `SqlConsts.java` — SQL 常量不变
