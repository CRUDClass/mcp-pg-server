package com.crudclass.mcpserver.pg.service.impl;

import com.crudclass.mcpserver.pg.constants.SqlConsts;
import com.crudclass.mcpserver.pg.enums.McpErrorCode;
import com.crudclass.mcpserver.pg.enums.SqlType;
import com.crudclass.mcpserver.pg.error.McpBusinessException;
import com.crudclass.mcpserver.pg.service.PgService;
import com.crudclass.mcpserver.pg.service.SqlExecutor;
import com.crudclass.mcpserver.pg.service.SqlParseResult;
import com.crudclass.mcpserver.pg.service.SqlValidator;
import lombok.AllArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Pattern;

/**
 * @author LiWenBo
 * @date 2026/05/13
 **/
@Service
@AllArgsConstructor
public class PgServiceImpl implements PgService {

    private final SqlValidator validator;
    private final JdbcTemplate jdbcTemplate;
    private final SqlExecutor executor;

    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 1000;

    private static final Pattern TABLE_NAME_PATTERN = Pattern.compile("^\\w+$");
    private static final String KEY_TABLE_COMMENT = "tableComment";




    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> executeBatch(List<String> sqls, Boolean confirm) {

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
        for (int i = 0; i < sqls.size(); i++) {
            String sql = sqls.get(i);
            SqlType type = types.get(i);
            SqlParseResult parseResult = new SqlParseResult(sql, null, type);
            results.add(executor.execute(parseResult));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("results", results);
        result.put("totalCount", results.size());
        result.put("executionTimeMs", System.currentTimeMillis() - start);
        return result;
    }


    @Override
    public Map<String, Object> executeQuery(
            String sql,
            Integer limit,
            Integer offset) {

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

    @Override
    public Map<String, Object> executeInsert(
            String sql,
            Boolean confirm) {

        boolean shouldExecute = confirm != null && confirm;
        SqlParseResult parsed = validator.validate(sql, SqlType.INSERT);

        if (!shouldExecute) {
            return executor.preview(parsed);
        }
        return executor.execute(parsed);
    }


    @Override
    public Map<String, Object> executeUpdate(
            String sql,
            Boolean confirm) {

        boolean shouldExecute = confirm != null && confirm;
        SqlParseResult parsed = validator.validate(sql, SqlType.UPDATE);

        if (!shouldExecute) {
            return executor.preview(parsed);
        }
        return executor.execute(parsed);
    }


    @Override
    public Map<String, Object> executeDdl(String sql, Boolean confirm) {

        boolean shouldExecute = confirm != null && confirm;
        SqlType expectedType = SqlValidator.quickDetectType(sql);
        if (expectedType != SqlType.CREATE_TABLE && expectedType != SqlType.DROP_TABLE) {
            throw new McpBusinessException(McpErrorCode.FORBIDDEN_OPERATION, "仅支持 CREATE TABLE 和 DROP TABLE");
        }

        SqlParseResult parsed = validator.validate(sql, expectedType);

        if (!shouldExecute) {
            return executor.preview(parsed);
        }
        return executor.execute(parsed);
    }

    @Override
    public Map<String, Object> executeDelete(String sql, Boolean confirm) {

        boolean shouldExecute = confirm != null && confirm;
        SqlParseResult parsed = validator.validate(sql, SqlType.DELETE);

        if (!shouldExecute) {
            return executor.preview(parsed);
        }
        return executor.execute(parsed);
    }


    @Override
    public Map<String, Object> listTables() {
        List<Map<String, Object>> tables = jdbcTemplate.queryForList(SqlConsts.SQL_TABLE_LIST);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("tables", tables);
        result.put("count", tables.size());
        return result;
    }

    @Override
    public Map<String, Object> describeTable(String tableName) {

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
        List<Map<String, Object>> indexes = jdbcTemplate.queryForList(SqlConsts.SQL_INDEXES, name);
        List<Map<String, Object>> foreignKeys = jdbcTemplate.queryForList(SqlConsts.SQL_FOREIGN_KEYS, name);
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

    private SqlType quickDetect(String sql) {
        return SqlValidator.quickDetectType(sql);
    }

    private Set<String> loadPrimaryKeys(String tableName) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(SqlConsts.SQL_PRIMARY_KEYS, tableName);
        Set<String> pkSet = new LinkedHashSet<>();
        for (Map<String, Object> row : rows) {
            pkSet.add((String) row.get("name"));
        }
        return pkSet;
    }

    private List<Map<String, Object>> loadColumns(String tableName, Set<String> primaryKeys) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(SqlConsts.SQL_COLUMNS, tableName);
        for (Map<String, Object> col : rows) {
            String colName = (String) col.get("name");
            col.put("isPrimaryKey", primaryKeys.contains(colName));
        }
        return rows;
    }

    private String loadTableComment(String tableName) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(SqlConsts.SQL_TABLE_COMMENT, tableName);
        if (rows.isEmpty() || rows.get(0).get(KEY_TABLE_COMMENT) == null) {
            return null;
        }
        return (String) rows.get(0).get(KEY_TABLE_COMMENT);
    }
}
