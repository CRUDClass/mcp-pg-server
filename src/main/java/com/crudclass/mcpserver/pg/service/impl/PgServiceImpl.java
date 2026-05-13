package com.crudclass.mcpserver.pg.service.impl;

import com.crudclass.mcpserver.pg.config.ToolMessages;
import com.crudclass.mcpserver.pg.constants.SqlConsts;
import com.crudclass.mcpserver.pg.enums.McpErrorCode;
import com.crudclass.mcpserver.pg.enums.SqlType;
import com.crudclass.mcpserver.pg.error.McpBusinessException;
import com.crudclass.mcpserver.pg.service.PgService;
import com.crudclass.mcpserver.pg.service.SqlExecutor;
import com.crudclass.mcpserver.pg.service.SqlParseResult;
import com.crudclass.mcpserver.pg.service.SqlValidator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Pattern;

/**
 * {@link PgService} 的唯一实现，负责 SQL 验证、执行和元数据查询的统一调度。
 * <p>
 * 委托 {@link SqlValidator} 进行安全校验，委托 {@link SqlExecutor} 进行实际 JDBC 执行。
 * 写操作（INSERT/UPDATE/DELETE/DDL/BATCH）采用两阶段预览/确认模式：
 * 首次调用返回预览，设置 confirm=true 后才真正执行。
 * <p>
 * SELECT 查询自动追加 LIMIT+1，通过多取一行来检测是否有更多数据（hasMore 信号）。
 *
 * @author CRUDClass
 * @date 2026/05/13
 **/
@Service
public class PgServiceImpl implements PgService {

    private final SqlValidator validator;
    private final JdbcTemplate jdbcTemplate;
    private final SqlExecutor executor;
    private final ToolMessages messages;

    public PgServiceImpl(SqlValidator validator, JdbcTemplate jdbcTemplate,
                         SqlExecutor executor, ToolMessages messages) {
        this.validator = validator;
        this.jdbcTemplate = jdbcTemplate;
        this.executor = executor;
        this.messages = messages;
    }

    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 1000;

    private static final Pattern TABLE_NAME_PATTERN = Pattern.compile("^\\w+$");
    private static final String KEY_TABLE_COMMENT = "tableComment";


    /**
     * 在单个事务中批量执行多条 SQL。
     * <p>
     * 流程：
     * <ol>
     *   <li>对每条 SQL 做快速类型检测 + JSqlParser 验证</li>
     *   <li>confirm 为 false/null：返回预览（含操作类型列表和提示消息）</li>
     *   <li>confirm 为 true：在事务中逐条执行，汇总耗时和结果</li>
     * </ol>
     *
     * @param sqls    SQL 语句列表
     * @param confirm 设为 true 确认执行
     * @return 执行结果或预览
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> executeBatch(List<String> sqls, Boolean confirm) {

        boolean shouldExecute = confirm != null && confirm;

        // ---- 校验阶段：每条 SQL 先经过快速检测和 JSqlParser 验证 ----
        List<SqlType> types = new ArrayList<>();
        for (String sql : sqls) {
            SqlType type = quickDetect(sql);
            validator.validate(sql, type);
            types.add(type);
        }

        // ---- 预览模式：校验通过但未实际执行 ----
        if (!shouldExecute) {
            Map<String, Object> preview = new LinkedHashMap<>();
            preview.put("success", true);
            preview.put("operations", types.stream().map(Enum::name).toList());
            preview.put("sqls", sqls);
            preview.put("message", messages.batchPreviewMessage(sqls.size()));
            preview.put("actionRequired", "confirm");
            return preview;
        }

        // ---- 执行模式：事务中逐条执行 ----
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


    /**
     * 执行只读 SELECT 查询。
     * <p>
     * 关键行为：
     * <ul>
     *   <li>如果 SQL 中没有 LIMIT 子句，自动追加 LIMIT (n+1)，
     *       多取一行用于判断 hasMore（分页信号）</li>
     *   <li>如果多取到了额外行，截断返回行数到 effectiveLimit，设置 hasMore=true</li>
     *   <li>limit 被限制在 1~1000 范围内</li>
     * </ul>
     *
     * @param sql    SELECT 查询语句
     * @param limit  返回行数上限（默认 100，最大 1000）
     * @param offset 跳过行数（默认 0）
     * @return 查询结果，含 rows、rowCount、hasMore、limit、offset
     */
    @Override
    public Map<String, Object> executeQuery(
            String sql,
            Integer limit,
            Integer offset) {

        int effectiveLimit = (limit != null && limit > 0) ? Math.min(limit, MAX_LIMIT) : DEFAULT_LIMIT;
        int effectiveOffset = (offset != null && offset >= 0) ? offset : 0;

        SqlParseResult parsed = validator.validate(sql, SqlType.SELECT);

        String finalSql = parsed.originalSql();
        // 如果 SQL 中没有 LIMIT，自动追加 LIMIT (n+1) 以检测 hasMore
        if (!finalSql.toUpperCase().contains("LIMIT")) {
            finalSql = finalSql.trim();
            if (finalSql.endsWith(";")) {
                finalSql = finalSql.substring(0, finalSql.length() - 1);
            }
            finalSql = finalSql + " LIMIT " + (effectiveLimit + 1) + " OFFSET " + effectiveOffset;
        }

        SqlParseResult executeResult = new SqlParseResult(finalSql, null, SqlType.SELECT);
        Map<String, Object> result = executor.execute(executeResult);

        // 多取一行判断是否还有更多数据
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

    /**
     * 执行 INSERT 插入语句，两阶段预览/确认模式。
     *
     * @param sql     INSERT 语句
     * @param confirm 设为 true 确认执行
     * @return 执行结果或预览
     */
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


    /**
     * 执行 UPDATE 更新语句，两阶段预览/确认模式。
     *
     * @param sql     UPDATE 语句
     * @param confirm 设为 true 确认执行
     * @return 执行结果或预览
     */
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


    /**
     * 执行 DDL 语句（仅限 CREATE TABLE 和 DROP TABLE），两阶段预览/确认模式。
     * <p>
     * 通过前缀快速检测 SQL 类型，额外校验只允许建表和删表两类 DDL。
     *
     * @param sql     DDL 语句
     * @param confirm 设为 true 确认执行
     * @return 执行结果或预览
     * @throws McpBusinessException 如果 SQL 类型不是 CREATE TABLE 或 DROP TABLE
     */
    @Override
    public Map<String, Object> executeDdl(String sql, Boolean confirm) {

        boolean shouldExecute = confirm != null && confirm;
        // 快速检测 SQL 类型，只允许建表和删表
        SqlType expectedType = validator.quickDetectType(sql);
        if (expectedType != SqlType.CREATE_TABLE && expectedType != SqlType.DROP_TABLE) {
            throw new McpBusinessException(McpErrorCode.FORBIDDEN_OPERATION,
                    messages.ddlOnlySupport(), (messages != null && messages.isEnglish()) ? "en" : "zh");
        }

        SqlParseResult parsed = validator.validate(sql, expectedType);

        if (!shouldExecute) {
            return executor.preview(parsed);
        }
        return executor.execute(parsed);
    }

    /**
     * 执行 DELETE 删除语句，两阶段预览/确认模式。
     *
     * @param sql     DELETE 语句
     * @param confirm 设为 true 确认执行
     * @return 执行结果或预览
     */
    @Override
    public Map<String, Object> executeDelete(String sql, Boolean confirm) {

        boolean shouldExecute = confirm != null && confirm;
        SqlParseResult parsed = validator.validate(sql, SqlType.DELETE);

        if (!shouldExecute) {
            return executor.preview(parsed);
        }
        return executor.execute(parsed);
    }


    /**
     * 列出 public schema 下所有表名及注释。
     * <p>
     * 执行 {@link SqlConsts#SQL_TABLE_LIST} 查询系统目录表。
     *
     * @return 含 tables 列表和 count 计数
     */
    @Override
    public Map<String, Object> listTables() {
        List<Map<String, Object>> tables = jdbcTemplate.queryForList(SqlConsts.SQL_TABLE_LIST);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("tables", tables);
        result.put("count", tables.size());
        return result;
    }

    /**
     * 查询某张表的完整元数据（列、主键、索引、外键、注释）。
     * <p>
     * 流程：
     * <ol>
     *   <li>校验表名字符合法性（只允许字母数字下划线）</li>
     *   <li>查询主键列表</li>
     *   <li>查询列信息并标记 isPrimaryKey</li>
     *   <li>查询索引列表</li>
     *   <li>查询外键列表</li>
     *   <li>查询表注释</li>
     * </ol>
     *
     * @param tableName 表名
     * @return 含 columns、primaryKeys、indexes、foreignKeys、tableComment
     * @throws McpBusinessException 如果表名为空或包含非法字符
     */
    @Override
    public Map<String, Object> describeTable(String tableName) {

        // ---- 表名校验 ----
        if (tableName == null || tableName.isBlank()) {
            throw new McpBusinessException(McpErrorCode.VALIDATION_FAILED,
                    messages.tableNameNotEmpty(), (messages != null && messages.isEnglish()) ? "en" : "zh");
        }
        if (!TABLE_NAME_PATTERN.matcher(tableName.trim()).matches()) {
            throw new McpBusinessException(McpErrorCode.VALIDATION_FAILED,
                    messages.illegalTableName(tableName), (messages != null && messages.isEnglish()) ? "en" : "zh");
        }

        String name = tableName.trim();

        // ---- 查询元数据 ----
        Set<String> primaryKeyNames = loadPrimaryKeys(name);
        List<Map<String, Object>> columns = loadColumns(name, primaryKeyNames);
        List<Map<String, Object>> indexes = jdbcTemplate.queryForList(SqlConsts.SQL_INDEXES, name);
        List<Map<String, Object>> foreignKeys = jdbcTemplate.queryForList(SqlConsts.SQL_FOREIGN_KEYS, name);
        String tableComment = loadTableComment(name);

        // ---- 组装结果 ----
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

    /**
     * 通过前缀匹配快速检测 SQL 类型，委托 {@link SqlValidator#quickDetectType(String)}。
     */
    private SqlType quickDetect(String sql) {
        return validator.quickDetectType(sql);
    }

    /**
     * 加载指定表的主键列名集合。
     */
    private Set<String> loadPrimaryKeys(String tableName) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(SqlConsts.SQL_PRIMARY_KEYS, tableName);
        Set<String> pkSet = new LinkedHashSet<>();
        for (Map<String, Object> row : rows) {
            pkSet.add((String) row.get("name"));
        }
        return pkSet;
    }

    /**
     * 加载指定表的列信息，并在每列上标记 isPrimaryKey。
     */
    private List<Map<String, Object>> loadColumns(String tableName, Set<String> primaryKeys) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(SqlConsts.SQL_COLUMNS, tableName);
        for (Map<String, Object> col : rows) {
            String colName = (String) col.get("name");
            col.put("isPrimaryKey", primaryKeys.contains(colName));
        }
        return rows;
    }

    /**
     * 加载指定表的注释文本。
     */
    private String loadTableComment(String tableName) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(SqlConsts.SQL_TABLE_COMMENT, tableName);
        if (rows.isEmpty() || rows.get(0).get(KEY_TABLE_COMMENT) == null) {
            return null;
        }
        return (String) rows.get(0).get(KEY_TABLE_COMMENT);
    }
}
