package com.crudclass.mcpserver.pg.tool;

import com.crudclass.mcpserver.pg.error.McpBusinessException;
import com.crudclass.mcpserver.pg.enums.McpErrorCode;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;

@Component
public class DescribeTableTool {

    private static final Pattern TABLE_NAME_PATTERN = Pattern.compile("^\\w+$");
    private static final String KEY_TABLE_COMMENT = "tableComment";

    private static final String SQL_COLUMNS = """
            SELECT c.column_name         AS name,
                   c.data_type            AS type,
                   c.is_nullable          AS nullable,
                   c.column_default       AS "defaultValue",
                   pg_catalog.col_description(pc.oid, c.ordinal_position::int) AS comment
            FROM information_schema.columns c
            JOIN pg_catalog.pg_class pc ON pc.relname = c.table_name
                AND pc.relnamespace = (SELECT oid FROM pg_catalog.pg_namespace WHERE nspname = 'public')
            WHERE c.table_schema = 'public'
              AND c.table_name = ?
            ORDER BY c.ordinal_position
            """;

    private static final String SQL_PRIMARY_KEYS = """
            SELECT kcu.column_name AS name
            FROM information_schema.table_constraints tc
            JOIN information_schema.key_column_usage kcu
                ON tc.constraint_name = kcu.constraint_name
                AND tc.table_schema = kcu.table_schema
            WHERE tc.constraint_type = 'PRIMARY KEY'
              AND tc.table_schema = 'public'
              AND tc.table_name = ?
            ORDER BY kcu.ordinal_position
            """;

    private static final String SQL_INDEXES = """
            SELECT i.relname       AS name,
                   idx.indisunique AS "unique",
                   (SELECT array_to_string(
                       array_agg(att.attname ORDER BY array_position(idx.indkey::int[], att.attnum)), ', ')
                    FROM pg_attribute att
                    WHERE att.attrelid = t.oid AND att.attnum = ANY(idx.indkey)) AS columns
            FROM pg_index idx
            JOIN pg_class i ON i.oid = idx.indexrelid
            JOIN pg_class t ON t.oid = idx.indrelid
            JOIN pg_namespace ns ON ns.oid = t.relnamespace
            WHERE t.relname = ?
              AND ns.nspname = 'public'
              AND idx.indisprimary = false
            ORDER BY i.relname
            """;

    private static final String SQL_FOREIGN_KEYS = """
            SELECT con.conname     AS name,
                   (SELECT array_to_string(
                       array_agg(att.attname ORDER BY ord.n), ', ')
                    FROM unnest(con.conkey) WITH ORDINALITY AS ord(col, n)
                    JOIN pg_attribute att ON att.attrelid = con.conrelid AND att.attnum = ord.col) AS "sourceColumns",
                   confrel.relname AS "targetTable",
                   (SELECT array_to_string(
                       array_agg(att.attname ORDER BY ord.n), ', ')
                    FROM unnest(con.confkey) WITH ORDINALITY AS ord(col, n)
                    JOIN pg_attribute att ON att.attrelid = con.confrelid AND att.attnum = ord.col) AS "targetColumns"
            FROM pg_constraint con
            JOIN pg_class rel ON rel.oid = con.conrelid
            JOIN pg_class confrel ON confrel.oid = con.confrelid
            JOIN pg_namespace nsp ON nsp.oid = rel.relnamespace
            WHERE rel.relname = ?
              AND nsp.nspname = 'public'
              AND con.contype = 'f'
            """;

    private static final String SQL_TABLE_COMMENT = """
            SELECT pg_catalog.obj_description(pc.oid, 'pg_class') AS "tableComment"
            FROM pg_catalog.pg_class pc
            JOIN pg_catalog.pg_namespace ns ON ns.oid = pc.relnamespace
            WHERE pc.relname = ? AND ns.nspname = 'public'
            """;

    private final JdbcTemplate jdbcTemplate;

    public DescribeTableTool(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Tool(description = "查询 public schema 下某张表的完整元数据（列、主键、索引、外键、注释）")
    public Map<String, Object> describeTable(
            @ToolParam(description = "表名") String tableName) {

        if (tableName == null || tableName.isBlank()) {
            throw new McpBusinessException(McpErrorCode.VALIDATION_FAILED, "tableName 不能为空");
        }
        if (!TABLE_NAME_PATTERN.matcher(tableName.trim()).matches()) {
            throw new McpBusinessException(McpErrorCode.VALIDATION_FAILED,
                    "非法表名: " + tableName);
        }

        String name = tableName.trim();

        Set<String> primaryKeyNames = loadPrimaryKeys(name);
        List<Map<String, Object>> columns = loadColumns(name, primaryKeyNames);
        List<Map<String, Object>> indexes = jdbcTemplate.queryForList(SQL_INDEXES, name);
        List<Map<String, Object>> foreignKeys = jdbcTemplate.queryForList(SQL_FOREIGN_KEYS, name);
        String tableComment = loadTableComment(name);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("tableName", name);
        result.put(KEY_TABLE_COMMENT, tableComment);
        result.put("columns", columns);
        result.put("primaryKeys", new ArrayList<>(primaryKeyNames));
        result.put("indexes", indexes);
        result.put("foreignKeys", foreignKeys);
        return result;
    }

    private Set<String> loadPrimaryKeys(String tableName) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(SQL_PRIMARY_KEYS, tableName);
        Set<String> pkSet = new LinkedHashSet<>();
        for (Map<String, Object> row : rows) {
            pkSet.add((String) row.get("name"));
        }
        return pkSet;
    }

    private List<Map<String, Object>> loadColumns(String tableName, Set<String> primaryKeys) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(SQL_COLUMNS, tableName);
        for (Map<String, Object> col : rows) {
            String colName = (String) col.get("name");
            col.put("isPrimaryKey", primaryKeys.contains(colName));
        }
        return rows;
    }

    private String loadTableComment(String tableName) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(SQL_TABLE_COMMENT, tableName);
        if (rows.isEmpty() || rows.get(0).get(KEY_TABLE_COMMENT) == null) {
            return null;
        }
        return (String) rows.get(0).get(KEY_TABLE_COMMENT);
    }
}
