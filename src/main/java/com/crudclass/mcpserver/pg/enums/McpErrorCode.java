package com.crudclass.mcpserver.pg.enums;

/**
 * Standard error codes for the MCP PostgreSQL server.
 * <p>
 * Each code carries a bilingual (zh/en) message suitable for both LLM consumption
 * and developer debugging. The codes are surfaced to the MCP client via
 * {@link com.crudclass.mcpserver.pg.error.McpBusinessException} and mapped to HTTP responses by
 * {@link com.crudclass.mcpserver.pg.error.ExceptionMapper}.
 *
 * @author CRUDClass
 */
public enum McpErrorCode {
    /**
     * SQL 不能为空
     */
    SQL_EMPTY("SQL 不能为空 / SQL cannot be empty"),
    /**
     * SQL 解析失败
     */
    SQL_PARSE_ERROR("SQL 解析失败 / SQL parse error"),
    /**
     * 不允许执行多条 SQL
     */
    MULTI_STATEMENT("不允许执行多条 SQL / Multiple statements not allowed"),
    /**
     * 禁止的 SQL 操作
     */
    FORBIDDEN_OPERATION("禁止的 SQL 操作 / Forbidden SQL operation"),
    /**
     * SQL 类型不匹配
     */
    TYPE_MISMATCH("SQL 类型不匹配 / SQL type mismatch"),
    /**
     * 禁止可写 CTE
     */
    WRITABLE_CTE("禁止可写 CTE / Writable CTE not allowed"),
    /**
     * 查询超时
     */
    QUERY_TIMEOUT("查询超时 / Query timeout"),
    /**
     * SQL 执行失败
     */
    EXECUTION_FAILED("SQL 执行失败 / SQL execution failed"),
    /**
     * 幂等冲突: 相同SQL已在执行中
     */
    IDEMPOTENCY_CONFLICT("幂等冲突: 相同SQL已在执行中 / Idempotency conflict"),
    /**
     * SQL 校验失败
     */
    VALIDATION_FAILED("SQL 校验失败 / SQL validation failed");

    private final String message;

    McpErrorCode(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
