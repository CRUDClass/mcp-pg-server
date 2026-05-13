package com.crudclass.mcpserver.pg.tool;

import com.crudclass.mcpserver.pg.service.PgService;
import com.crudclass.mcpserver.pg.service.SqlValidator;
import lombok.AllArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * MCP tool that executes multiple SQL statements in a single transaction.
 * <p>
 * Annotated with {@link org.springframework.transaction.annotation.Transactional}
 * to guarantee atomicity. Each SQL is validated individually via
 * {@link SqlValidator} before execution. Supports the two-phase preview/confirm
 * pattern — on the first call each statement is validated but not executed;
 * on the confirmed call all statements run sequentially.
 * <p>
 * Uses a local {@code quickDetect} helper to determine the expected SQL type
 * for each statement before the more thorough JSqlParser validation.
 */
@Component
@AllArgsConstructor
public class BatchTool {

    private final PgService pgService;

    @Tool(description = "在单个事务中执行多条 SQL。首次返回预览，设置 confirm=true 确认执行。")
    public Map<String, Object> executeBatch(
            @ToolParam(description = "要在事务中执行的 SQL 语句数组") List<String> sqls,
            @ToolParam(description = "二次调用时设为 true 确认执行", required = false) Boolean confirm) {
        return pgService.executeBatch(sqls, confirm);
    }
}
