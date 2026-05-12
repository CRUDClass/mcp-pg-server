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
 * MCP tool for {@code INSERT} statements.
 * <p>
 * Two-phase execution: the first call (without {@code confirm}) returns a
 * structured preview with {@code actionRequired:"confirm"}. The LLM must
 * set {@code confirm=true} on the second call to actually execute the SQL.
 */
@Component
public class InsertTool {

    private final SqlValidator validator;
    private final SqlExecutor executor;

    public InsertTool(SqlValidator validator, SqlExecutor executor) {
        this.validator = validator;
        this.executor = executor;
    }

    @Tool(description = "向 PostgreSQL 表插入数据。首次返回预览，设置 confirm=true 确认执行。")
    public Map<String, Object> executeInsert(
            @ToolParam(description = "PostgreSQL INSERT 语句") String sql,
            @ToolParam(description = "二次调用时设为 true 确认执行", required = false) Boolean confirm) {

        boolean shouldExecute = confirm != null && confirm;
        SqlParseResult parsed = validator.validate(sql, SqlType.INSERT);

        if (!shouldExecute) {
            return executor.preview(parsed);
        }
        return executor.execute(parsed);
    }
}
