package com.crudclass.mcpserver.pg.tool;

import com.crudclass.mcpserver.pg.service.PgService;
import lombok.AllArgsConstructor;
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
@AllArgsConstructor
public class DeleteTool {

    private final PgService pgService;

    @Tool(description = "从 PostgreSQL 表中删除数据。首次返回预览，设置 confirm=true 确认执行。")
    public Map<String, Object> executeDelete(
            @ToolParam(description = "PostgreSQL DELETE 语句") String sql,
            @ToolParam(description = "二次调用时设为 true 确认执行", required = false) Boolean confirm) {
        return pgService.executeDelete(sql, confirm);
    }
}
