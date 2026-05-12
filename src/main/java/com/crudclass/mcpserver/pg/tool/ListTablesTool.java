package com.crudclass.mcpserver.pg.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ListTablesTool {

    private static final String SQL = """
            SELECT t.tablename AS "tableName",
                   pg_catalog.obj_description(c.oid, 'pg_class') AS "comment"
            FROM pg_catalog.pg_tables t
            JOIN pg_catalog.pg_class c ON c.relname = t.tablename
            JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace AND n.nspname = 'public'
            WHERE t.schemaname = 'public'
            ORDER BY t.tablename
            """;

    private final JdbcTemplate jdbcTemplate;

    public ListTablesTool(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Tool(description = "列出 public schema 下所有表名及注释")
    public Map<String, Object> listTables() {
        List<Map<String, Object>> tables = jdbcTemplate.queryForList(SQL);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("tables", tables);
        result.put("count", tables.size());
        return result;
    }
}
