package com.crudclass.mcpserver.pg.tool;

import com.crudclass.mcpserver.pg.service.PgService;
import lombok.AllArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * MCP tool for {@code UPDATE} statements.
 * <p>
 * Follows the same two-phase preview/confirm pattern as {@link InsertTool}.
 *
 * @author CRUDClass
 */
@Component
@AllArgsConstructor
public class UpdateTool {

    private final PgService pgService;
    @Tool(description = "更新 PostgreSQL 表中的数据。首次返回预览，设置 confirm=true 确认执行。")
    public Map<String, Object> executeUpdate(
            @ToolParam(description = "PostgreSQL UPDATE 语句") String sql,
            @ToolParam(description = "二次调用时设为 true 确认执行", required = false) Boolean confirm) {

        return pgService.executeUpdate(sql, confirm);
    }
}
