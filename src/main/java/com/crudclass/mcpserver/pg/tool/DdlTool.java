package com.crudclass.mcpserver.pg.tool;

import com.crudclass.mcpserver.pg.enums.McpErrorCode;
import com.crudclass.mcpserver.pg.enums.SqlType;
import com.crudclass.mcpserver.pg.error.McpBusinessException;
import com.crudclass.mcpserver.pg.service.SqlExecutor;
import com.crudclass.mcpserver.pg.service.SqlParseResult;
import com.crudclass.mcpserver.pg.service.SqlValidator;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * MCP tool for {@code CREATE TABLE} and {@code DROP TABLE} statements.
 * <p>
 * Determines the expected SQL type via a simple prefix check before delegating
 * full validation to {@link SqlValidator}. Follows the same two-phase
 * preview/confirm pattern as other write tools.
 */
@Component
public class DdlTool {

    private final SqlValidator validator;
    private final SqlExecutor executor;

    public DdlTool(SqlValidator validator, SqlExecutor executor) {
        this.validator = validator;
        this.executor = executor;
    }

    @Tool(description = "在 PostgreSQL 中创建或删除表。首次返回预览，设置 confirm=true 确认执行。")
    public Map<String, Object> executeDdl(
            @ToolParam(description = "PostgreSQL CREATE TABLE 或 DROP TABLE 语句") String sql,
            @ToolParam(description = "二次调用时设为 true 确认执行", required = false) Boolean confirm) {

        boolean shouldExecute = confirm != null && confirm;
        SqlType expectedType = SqlValidator.quickDetectType(sql);
        if (expectedType != SqlType.CREATE_TABLE
                && expectedType != SqlType.DROP_TABLE) {
            throw new McpBusinessException(McpErrorCode.FORBIDDEN_OPERATION,
                    "仅支持 CREATE TABLE 和 DROP TABLE");
        }

        SqlParseResult parsed = validator.validate(sql, expectedType);

        if (!shouldExecute) {
            return executor.preview(parsed);
        }
        return executor.execute(parsed);
    }
}
