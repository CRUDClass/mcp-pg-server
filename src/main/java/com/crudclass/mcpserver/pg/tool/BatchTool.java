package com.crudclass.mcpserver.pg.tool;

import com.crudclass.mcpserver.pg.enums.SqlType;
import com.crudclass.mcpserver.pg.service.SqlValidator;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

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
public class BatchTool {

    private final SqlValidator validator;
    private final JdbcTemplate jdbcTemplate;

    public BatchTool(SqlValidator validator, JdbcTemplate jdbcTemplate) {
        this.validator = validator;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Tool(description = "在单个事务中执行多条 SQL。首次返回预览，设置 confirm=true 确认执行。")
    @Transactional
    public Map<String, Object> executeBatch(
            @ToolParam(description = "要在事务中执行的 SQL 语句数组") List<String> sqls,
            @ToolParam(description = "二次调用时设为 true 确认执行", required = false) Boolean confirm) {

        boolean shouldExecute = confirm != null && confirm;

        List<SqlType> types = new ArrayList<>();
        for (String sql : sqls) {
            SqlType type = quickDetect(sql);
            validator.validate(sql, type);
            types.add(type);
        }

        if (!shouldExecute) {
            Map<String, Object> preview = new LinkedHashMap<>();
            preview.put("success", true);
            preview.put("operations", types.stream().map(Enum::name).toList());
            preview.put("sqls", sqls);
            preview.put("message", "预览模式，共 " + sqls.size() + " 条 SQL 校验通过，未实际执行。请重新调用并设置 confirm=true。");
            preview.put("actionRequired", "confirm");
            return preview;
        }

        long start = System.currentTimeMillis();
        List<Map<String, Object>> results = new ArrayList<>();
        for (String sql : sqls) {
            jdbcTemplate.execute(sql);
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("sql", sql);
            r.put("status", "ok");
            results.add(r);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("results", results);
        result.put("totalCount", results.size());
        result.put("executionTimeMs", System.currentTimeMillis() - start);
        return result;
    }

    private SqlType quickDetect(String sql) {
        return SqlValidator.quickDetectType(sql);
    }
}
