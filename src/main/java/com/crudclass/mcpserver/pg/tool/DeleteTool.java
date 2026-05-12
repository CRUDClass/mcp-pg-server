package com.crudclass.mcpserver.pg.tool;

import com.crudclass.mcpserver.pg.enums.SqlType;
import com.crudclass.mcpserver.pg.service.SqlExecutor;
import com.crudclass.mcpserver.pg.service.SqlParseResult;
import com.crudclass.mcpserver.pg.service.SqlValidator;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * MCP tool for {@code DELETE} statements.
 * <p>
 * Follows the same two-phase preview/confirm pattern as {@link InsertTool}.
 */
@Component
public class DeleteTool {

    private final SqlValidator validator;
    private final SqlExecutor executor;

    public DeleteTool(SqlValidator validator, SqlExecutor executor) {
        this.validator = validator;
        this.executor = executor;
    }

    @Tool(description = "从 PostgreSQL 表中删除数据。首次返回预览，设置 confirm=true 确认执行。")
    public Map<String, Object> executeDelete(
            @ToolParam(description = "PostgreSQL DELETE 语句") String sql,
            @ToolParam(description = "二次调用时设为 true 确认执行", required = false) Boolean confirm) {

        boolean shouldExecute = confirm != null && confirm;
        SqlParseResult parsed = validator.validate(sql, SqlType.DELETE);

        if (!shouldExecute) {
            return executor.preview(parsed);
        }
        return executor.execute(parsed);
    }
}
