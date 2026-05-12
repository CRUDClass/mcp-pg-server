package com.crudclass.mcpserver.pg.service;

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
 * JDBC executor with per-statement timeout, idempotency caching, and audit
 * logging for write operations.
 *
 * <h3>Key behaviours</h3>
 * <ul>
 *   <li><b>Timeout</b> — {@link JdbcTemplate#setQueryTimeout(int) 30 s} on
 *       every statement.</li>
 *   <li><b>Idempotency</b> — SHA-256 of the SQL text used as cache key in a
 *       Caffeine LRU cache (5 min TTL, 1 000 entries). Replayed writes return
 *       the cached result and log an {@code IDEMPOTENT_REPLAY} audit entry.</li>
 *   <li><b>Audit</b> — every non-SELECT operation writes a structured log line
 *       to the {@code AUDIT} logger (SLF4J). PostgreSQL {@code log_statement}
 *       complements this at the database layer.</li>
 *   <li><b>Error propagation</b> — JDBC exceptions are re-wrapped as
 *       {@link McpBusinessException}({@link McpErrorCode#EXECUTION_FAILED})
 *       instead of being silently caught.</li>
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

    public SqlExecutor(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.idempotencyCache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(5))
                .maximumSize(1000)
                .build();
    }

    /**
     * Executes the validated SQL with timeout enforcement, idempotency check,
     * and audit logging.
     *
     * @param parseResult the pre-validated parse result from {@link SqlValidator}
     * @return a map with keys {@code success, operation, sql, message} and
     *         operation-specific metrics
     * @throws McpBusinessException if the JDBC call fails
     */
    public Map<String, Object> execute(SqlParseResult parseResult) {
        String sql = parseResult.originalSql();
        SqlType sqlType = parseResult.sqlType();

        if (sqlType != SqlType.SELECT) {
            String idempotencyKey = sha256(sql);
            Map<String, Object> cached = idempotencyCache.getIfPresent(idempotencyKey);
            if (cached != null) {
                auditLog.info("AUDIT | type=IDEMPOTENT_REPLAY | operation={} | sql={}", sqlType, sql);
                return cached;
            }
        }

        jdbcTemplate.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
        long start = System.currentTimeMillis();

        try {
            Map<String, Object> result = doExecute(sql, sqlType, start);

            if (sqlType != SqlType.SELECT) {
                String idempotencyKey = sha256(sql);
                idempotencyCache.put(idempotencyKey, result);
            }

            if (sqlType != SqlType.SELECT) {
                auditLog.info("AUDIT | type=EXECUTE | operation={} | sql={} | elapsedMs={}",
                        sqlType, sql, System.currentTimeMillis() - start);
            }

            return result;
        } catch (QueryTimeoutException e) {
            throw new McpBusinessException(McpErrorCode.QUERY_TIMEOUT,
                    sqlType + ": " + e.getMessage());
        } catch (Exception e) {
            throw new McpBusinessException(McpErrorCode.EXECUTION_FAILED,
                    sqlType + ": " + e.getMessage());
        }
    }

    /**
     * Builds a preview response without executing any SQL.
     * Used by write tools on their first (unconfirmed) invocation.
     *
     * @param parseResult the pre-validated parse result
     * @return a map with {@code success:true} and {@code actionRequired:"confirm"}
     */
    public Map<String, Object> preview(SqlParseResult parseResult) {
        String sql = parseResult.originalSql();
        Map<String, Object> preview = new LinkedHashMap<>();
        preview.put("success", true);
        preview.put("operation", parseResult.sqlType().name());
        preview.put("sql", sql);
        preview.put(KEY_MESSAGE, "预览模式，SQL 校验通过，未实际执行。请重新调用并设置 confirm=true 确认执行。");
        preview.put("actionRequired", "confirm");
        return preview;
    }

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
                result.put(KEY_MESSAGE, "查询成功，返回 " + rows.size() + " 行");
            }
            case INSERT -> {
                int affected = jdbcTemplate.update(sql);
                result.put(KEY_AFFECTED_ROWS, affected);
                result.put(KEY_MESSAGE, "插入成功，影响 " + affected + " 行");
            }
            case UPDATE -> {
                int affected = jdbcTemplate.update(sql);
                result.put(KEY_AFFECTED_ROWS, affected);
                result.put(KEY_MESSAGE, "更新成功，影响 " + affected + " 行");
            }
            case DELETE -> {
                int affected = jdbcTemplate.update(sql);
                result.put(KEY_AFFECTED_ROWS, affected);
                result.put(KEY_MESSAGE, "删除成功，影响 " + affected + " 行");
            }
            case CREATE_TABLE -> {
                jdbcTemplate.execute(sql);
                result.put("tableName", extractTableName(sql));
                result.put(KEY_MESSAGE, "创建成功");
            }
            case DROP_TABLE -> {
                String tableName = extractTableName(sql);
                jdbcTemplate.execute(sql);
                result.put("tableName", tableName);
                result.put(KEY_MESSAGE, "删除成功");
            }
            default -> throw new McpBusinessException(McpErrorCode.EXECUTION_FAILED,
                    "Unsupported SQL type: " + sqlType);
        }

        result.put("executionTimeMs", System.currentTimeMillis() - start);
        return result;
    }

    private String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            return Integer.toHexString(input.hashCode());
        }
    }

    private String extractTableName(String sql) {
        String upper = sql.toUpperCase();
        int idx = upper.indexOf("CREATE TABLE");
        if (idx == -1) {
            idx = upper.indexOf("DROP TABLE");
        }
        if (idx == -1) {
            return UNKNOWN_TABLE;
        }
        String after = sql.substring(idx + 12).trim();
        return after.split("\\s+")[0].replaceAll("[;,(].*$", "");
    }
}
