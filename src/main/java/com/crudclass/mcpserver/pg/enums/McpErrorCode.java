package com.crudclass.mcpserver.pg.enums;

/**
 * Standard error codes for the MCP PostgreSQL server.
 * <p>
 * Each code carries separate zh and en messages. The locale-aware
 * {@link #getMessage(String)} picks the right one at runtime.
 *
 * @author CRUDClass
 */
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

    McpErrorCode(String zhMessage, String enMessage) {
        this.zhMessage = zhMessage;
        this.enMessage = enMessage;
    }

    public String getMessage(String locale) {
        return "en".equalsIgnoreCase(locale) ? enMessage : zhMessage;
    }

    public String getZhMessage() {
        return zhMessage;
    }

    public String getEnMessage() {
        return enMessage;
    }
}
