package com.crudclass.mcpserver.pg.tool;

import com.crudclass.mcpserver.pg.service.PgService;
import com.crudclass.mcpserver.pg.service.SqlValidator;
import lombok.AllArgsConstructor;
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
@AllArgsConstructor
public class DdlTool {

    private final PgService pgService;

    @Tool(description = "在 PostgreSQL 中创建或删除表。首次返回预览，设置 confirm=true 确认执行。")
    public Map<String, Object> executeDdl(
            @ToolParam(description = "PostgreSQL CREATE TABLE 或 DROP TABLE 语句") String sql,
            @ToolParam(description = "二次调用时设为 true 确认执行", required = false) Boolean confirm) {
        return pgService.executeDdl(sql, confirm);
    }
}
