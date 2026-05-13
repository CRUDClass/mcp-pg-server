package com.crudclass.mcpserver.pg.tool;

import com.crudclass.mcpserver.pg.service.PgService;
import lombok.AllArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * MCP tool for read-only {@code SELECT} queries (including {@code WITH} CTEs).
 * <p>
 * Automatically appends {@code LIMIT limit+1 OFFSET offset} when the SQL
 * has no explicit LIMIT clause. The extra row is fetched to compute the
 * {@code hasMore} signal for iterative pagination. The effective limit is
 * capped at {@value #MAX_LIMIT} rows.
 *
 * @author CRUDClass
 */
@Component
@AllArgsConstructor
public class QueryTool {

    private final PgService pgService;

    @Tool(description = "在 PostgreSQL 上执行只读 SELECT 查询。支持 SELECT 和 WITH (CTE)。")
    public Map<String, Object> executeQuery(
            @ToolParam(description = "PostgreSQL SELECT 或 WITH 查询语句") String sql,
            @ToolParam(description = "返回的最大行数 (1-1000, 默认 100)", required = false) Integer limit,
            @ToolParam(description = "跳过的行数 (默认 0)", required = false) Integer offset) {

        return pgService.executeQuery(sql, limit, offset);
    }
}
