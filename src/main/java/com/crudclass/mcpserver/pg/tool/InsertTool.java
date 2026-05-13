package com.crudclass.mcpserver.pg.tool;

import com.crudclass.mcpserver.pg.service.PgService;
import lombok.AllArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * MCP tool for {@code INSERT} statements.
 * <p>
 * Two-phase execution: the first call (without {@code confirm}) returns a
 * structured preview with {@code actionRequired:"confirm"}. The LLM must
 * set {@code confirm=true} on the second call to actually execute the SQL.
 *
 * @author CRUDClass
 */
@Component
@AllArgsConstructor
public class InsertTool {
    private final PgService pgService;
    @Tool(description = "向 PostgreSQL 表插入数据。首次返回预览，设置 confirm=true 确认执行。")
    public Map<String, Object> executeInsert(
            @ToolParam(description = "PostgreSQL INSERT 语句") String sql,
            @ToolParam(description = "二次调用时设为 true 确认执行", required = false) Boolean confirm) {

        return pgService.executeInsert(sql, confirm);
    }
}
