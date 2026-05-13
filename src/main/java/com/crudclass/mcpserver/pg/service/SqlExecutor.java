package com.crudclass.mcpserver.pg.service;

import com.crudclass.mcpserver.pg.config.ToolMessages;
import com.crudclass.mcpserver.pg.enums.McpErrorCode;
import com.crudclass.mcpserver.pg.enums.SqlType;
import com.crudclass.mcpserver.pg.error.McpBusinessException;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;

/**
 * JDBC 执行器，提供每语句超时控制、幂等性缓存和写操作审计日志。
 *
 * <h3>核心功能</h3>
 * <ul>
 *   <li><b>超时</b> — 每条语句设置 {@link JdbcTemplate#setQueryTimeout(int) 30秒} 查询超时</li>
 *   <li><b>幂等性</b> — 以 SQL 文本的 SHA-256 哈希作为缓存键，
 *       使用 Caffeine LRU 缓存（5分钟 TTL，1000条上限）。重复执行写入操作时
 *       直接返回缓存结果，并记录 {@code IDEMPOTENT_REPLAY} 审计日志</li>
 *   <li><b>审计</b> — 每个非 SELECT 操作写入结构化日志到 {@code AUDIT} 日志器，
 *       PostgreSQL 端的 {@code log_statement} 在数据库层补充此功能</li>
 *   <li><b>错误传播</b> — JDBC 异常统一包装为
 *       {@link McpBusinessException}({@link McpErrorCode#EXECUTION_FAILED})，
 *       避免静默吞掉异常</li>
 * </ul>
 * @author CRUDClass
 */
@Component
public class SqlExecutor {

    private static final Logger auditLog = LoggerFactory.getLogger("AUDIT");
    private static final int QUERY_TIMEOUT_SECONDS = 30;
    private static final String UNKNOWN_TABLE = "unknown";
    private static final String KEY_MESSAGE = "message";
    private static final String KEY_AFFECTED_ROWS = "affectedRows";

    private final JdbcTemplate jdbcTemplate;
    private final Cache<String, Map<String, Object>> idempotencyCache;
    private final ToolMessages messages;

    public SqlExecutor(JdbcTemplate jdbcTemplate, ToolMessages messages) {
        this.jdbcTemplate = jdbcTemplate;
        this.messages = messages;
        // 初始化 Caffeine LRU 缓存：5分钟过期，最大1000条
        this.idempotencyCache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(5))
                .maximumSize(1000)
                .build();
    }

    /**
     * 执行已验证的 SQL，包含超时控制、幂等性检查和审计日志。
     *
     * @param parseResult 来自 {@link SqlValidator} 的预校验解析结果
     * @return 包含 {@code success, operation, sql, message} 和操作相关指标的结果 Map
     * @throws McpBusinessException JDBC 调用失败时抛出
     */
    public Map<String, Object> execute(SqlParseResult parseResult) {
        String sql = parseResult.originalSql();
        SqlType sqlType = parseResult.sqlType();

        // 非 SELECT 操作：先检查幂等缓存
        if (sqlType != SqlType.SELECT) {
            String idempotencyKey = sha256(sql);
            Map<String, Object> cached = idempotencyCache.getIfPresent(idempotencyKey);
            if (cached != null) {
                // 命中缓存，直接返回，记录幂等重放审计
                auditLog.info("AUDIT | type=IDEMPOTENT_REPLAY | operation={} | sql={}", sqlType, sql);
                return cached;
            }
        }

        jdbcTemplate.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
        long start = System.currentTimeMillis();

        try {
            Map<String, Object> result = doExecute(sql, sqlType, start);

            // 非 SELECT 操作：存入幂等缓存
            if (sqlType != SqlType.SELECT) {
                String idempotencyKey = sha256(sql);
                idempotencyCache.put(idempotencyKey, result);
            }

            // 非 SELECT 操作：记录审计日志
            if (sqlType != SqlType.SELECT) {
                auditLog.info("AUDIT | type=EXECUTE | operation={} | sql={} | elapsedMs={}",
                        sqlType, sql, System.currentTimeMillis() - start);
            }

            return result;
        } catch (QueryTimeoutException e) {
            throw new McpBusinessException(McpErrorCode.QUERY_TIMEOUT,
                    sqlType + ": " + e.getMessage(), (messages != null && messages.isEnglish()) ? "en" : "zh");
        } catch (Exception e) {
            throw new McpBusinessException(McpErrorCode.EXECUTION_FAILED,
                    sqlType + ": " + e.getMessage(), (messages != null && messages.isEnglish()) ? "en" : "zh");
        }
    }

    /**
     * 构建预览响应，不实际执行 SQL。
     * 供写工具在首次（未确认）调用时使用。
     *
     * @param parseResult 预校验的解析结果
     * @return 包含 {@code success:true} 和 {@code actionRequired:"confirm"} 的结果 Map
     */
    public Map<String, Object> preview(SqlParseResult parseResult) {
        String sql = parseResult.originalSql();
        Map<String, Object> preview = new LinkedHashMap<>();
        preview.put("success", true);
        preview.put("operation", parseResult.sqlType().name());
        preview.put("sql", sql);
        preview.put(KEY_MESSAGE, messages.previewMessage());
        preview.put("actionRequired", "confirm");
        return preview;
    }

    /**
     * 根据 SQL 类型分派到对应的 JDBC 执行方法。
     * <p>
     * SELECT 使用 {@link JdbcTemplate#queryForList}，
     * INSERT/UPDATE/DELETE 使用 {@link JdbcTemplate#update}，
     * CREATE TABLE/DROP TABLE 使用 {@link JdbcTemplate#execute}。
     */
    private Map<String, Object> doExecute(String sql, SqlType sqlType, long start) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("operation", sqlType.name());
        result.put("sql", sql);

        switch (sqlType) {
            case SELECT -> {
                List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
                result.put("rows", rows);
                result.put("rowCount", rows.size());
                result.put(KEY_MESSAGE, messages.selectSuccess(rows.size()));
            }
            case INSERT -> {
                int affected = jdbcTemplate.update(sql);
                result.put(KEY_AFFECTED_ROWS, affected);
                result.put(KEY_MESSAGE, messages.insertSuccess(affected));
            }
            case UPDATE -> {
                int affected = jdbcTemplate.update(sql);
                result.put(KEY_AFFECTED_ROWS, affected);
                result.put(KEY_MESSAGE, messages.updateSuccess(affected));
            }
            case DELETE -> {
                int affected = jdbcTemplate.update(sql);
                result.put(KEY_AFFECTED_ROWS, affected);
                result.put(KEY_MESSAGE, messages.deleteSuccess(affected));
            }
            case CREATE_TABLE -> {
                jdbcTemplate.execute(sql);
                result.put("tableName", extractTableName(sql));
                result.put(KEY_MESSAGE, messages.createSuccess());
            }
            case DROP_TABLE -> {
                String tableName = extractTableName(sql);
                jdbcTemplate.execute(sql);
                result.put("tableName", tableName);
                result.put(KEY_MESSAGE, messages.dropSuccess());
            }
            default -> throw new McpBusinessException(McpErrorCode.EXECUTION_FAILED,
                    messages.unsupportedType() + ": " + sqlType, (messages != null && messages.isEnglish()) ? "en" : "zh");
        }

        result.put("executionTimeMs", System.currentTimeMillis() - start);
        return result;
    }

    /**
     * 计算字符串的 SHA-256 哈希（Base64 编码），用于幂等缓存键。
     * 如果 SHA-256 不可用，降级使用 hashCode 的十六进制表示。
     */
    private String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            return Integer.toHexString(input.hashCode());
        }
    }

    /**
     * 从 CREATE TABLE / DROP TABLE 语句中提取表名。
     * <p>
     * 通过定位关键字位置，截取后续第一个以空格分隔的标识符作为表名，
     * 并去除尾部的分号、逗号、括号等字符。
     */
    private String extractTableName(String sql) {
        String upper = sql.toUpperCase();
        int idx = upper.indexOf("CREATE TABLE");
        if (idx == -1) {
            idx = upper.indexOf("DROP TABLE");
        }
        if (idx == -1) {
            return UNKNOWN_TABLE;
        }
        // 跳过关键字（12个字符），取其后第一个以空白分隔的 token 作为表名
        String after = sql.substring(idx + 12).trim();
        return after.split("\\s+")[0].replaceAll("[;,(].*$", "");
    }
}
