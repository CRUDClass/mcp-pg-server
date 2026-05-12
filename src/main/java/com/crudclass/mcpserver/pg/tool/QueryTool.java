package com.crudclass.mcpserver.pg.tool;

import com.crudclass.mcpserver.pg.enums.SqlType;
import com.crudclass.mcpserver.pg.service.SqlExecutor;
import com.crudclass.mcpserver.pg.service.SqlParseResult;
import com.crudclass.mcpserver.pg.service.SqlValidator;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * MCP tool for read-only {@code SELECT} queries (including {@code WITH} CTEs).
 * <p>
 * Automatically appends {@code LIMIT limit+1 OFFSET offset} when the SQL
 * has no explicit LIMIT clause. The extra row is fetched to compute the
 * {@code hasMore} signal for iterative pagination. The effective limit is
 * capped at {@value #MAX_LIMIT} rows.
 */
@Component
public class QueryTool {

    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 1000;

    private final SqlValidator validator;
    private final SqlExecutor executor;

    public QueryTool(SqlValidator validator, SqlExecutor executor) {
        this.validator = validator;
        this.executor = executor;
    }

    @Tool(description = "在 PostgreSQL 上执行只读 SELECT 查询。支持 SELECT 和 WITH (CTE)。")
    public Map<String, Object> executeQuery(
            @ToolParam(description = "PostgreSQL SELECT 或 WITH 查询语句") String sql,
            @ToolParam(description = "返回的最大行数 (1-1000, 默认 100)", required = false) Integer limit,
            @ToolParam(description = "跳过的行数 (默认 0)", required = false) Integer offset) {

        int effectiveLimit = (limit != null && limit > 0) ? Math.min(limit, MAX_LIMIT) : DEFAULT_LIMIT;
        int effectiveOffset = (offset != null && offset >= 0) ? offset : 0;

        SqlParseResult parsed = validator.validate(sql, SqlType.SELECT);

        String finalSql = parsed.originalSql();
        if (!finalSql.toUpperCase().contains("LIMIT")) {
            finalSql = finalSql.trim();
            if (finalSql.endsWith(";")) {
                finalSql = finalSql.substring(0, finalSql.length() - 1);
            }
            finalSql = finalSql + " LIMIT " + (effectiveLimit + 1) + " OFFSET " + effectiveOffset;
        }

        SqlParseResult executeResult = new SqlParseResult(finalSql, null, SqlType.SELECT);
        Map<String, Object> result = executor.execute(executeResult);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get("rows");
        boolean hasMore = rows != null && rows.size() > effectiveLimit;
        if (hasMore) {
            rows = rows.subList(0, effectiveLimit);
            result.put("rows", rows);
            result.put("rowCount", effectiveLimit);
        }
        result.put("hasMore", hasMore);
        result.put("limit", effectiveLimit);
        result.put("offset", effectiveOffset);
        return result;
    }
}
