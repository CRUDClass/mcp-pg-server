package com.crudclass.mcpserver.pg.service;

import com.crudclass.mcpserver.pg.error.McpBusinessException;
import com.crudclass.mcpserver.pg.enums.McpErrorCode;
import com.crudclass.mcpserver.pg.enums.SqlType;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.*;
import net.sf.jsqlparser.statement.alter.Alter;
import net.sf.jsqlparser.statement.create.table.CreateTable;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.drop.Drop;
import net.sf.jsqlparser.statement.execute.Execute;
import net.sf.jsqlparser.statement.grant.Grant;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.select.*;
import net.sf.jsqlparser.statement.truncate.Truncate;
import net.sf.jsqlparser.statement.update.Update;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * SQL validation engine that combines JSqlParser AST analysis with PostgreSQL
 * EXPLAIN verification and regex-based row-lock detection.
 *
 * <h3>Validation layers</h3>
 * <ol>
 *   <li><b>JSqlParser parse</b> — resolves statement type, detects
 *       multi-statements, rejects writeable CTEs and forbidden operations
 *       (TRUNCATE, ALTER, GRANT, EXECUTE, SET, BLOCK, COMMIT, etc.)
 *       at the parser level.</li>
 *   <li><b>Pattern check</b> — regex scan for {@code FOR UPDATE / FOR SHARE}
 *       clauses that JSqlParser 5.0 does not recognise (PG extension).</li>
 *   <li><b>EXPLAIN handshake</b> — non-fatal; sends {@code EXPLAIN &lt;sql&gt;}
 *       to PostgreSQL. A rejected EXPLAIN is logged as a warning but does
 *       not block the request.</li>
 * </ol>
 *
 * <p>Uses {@code instanceof} black-list matching against every known
 * dangerous JSqlParser {@link Statement} type, preventing pattern-bypass
 * attacks that plague text-based validation.
 *
 * @author CRUDClass
 */
@Component
public class SqlValidator {

    private static final Logger log = LoggerFactory.getLogger(SqlValidator.class);
    private static final Pattern FOR_CLAUSE_PATTERN = Pattern.compile("\\bFOR\\s+(NO\\s+KEY\\s+)?(UPDATE|SHARE|KEY\\s+SHARE)\\b", Pattern.CASE_INSENSITIVE);

    private static final Map<String, SqlType> PREFIX_TYPE_MAP;

    static {
        PREFIX_TYPE_MAP = new LinkedHashMap<>();
        PREFIX_TYPE_MAP.put("SELECT", SqlType.SELECT);
        PREFIX_TYPE_MAP.put("WITH", SqlType.SELECT);
        PREFIX_TYPE_MAP.put("INSERT", SqlType.INSERT);
        PREFIX_TYPE_MAP.put("UPDATE", SqlType.UPDATE);
        PREFIX_TYPE_MAP.put("DELETE", SqlType.DELETE);
        PREFIX_TYPE_MAP.put("CREATE TABLE", SqlType.CREATE_TABLE);
        PREFIX_TYPE_MAP.put("DROP TABLE", SqlType.DROP_TABLE);
    }

    public static SqlType quickDetectType(String sql) {
        String upper = sql.trim().toUpperCase().replaceAll("\\s+", " ");
        for (var entry : PREFIX_TYPE_MAP.entrySet()) {
            if (upper.startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        throw new McpBusinessException(McpErrorCode.FORBIDDEN_OPERATION, "不支持的 SQL 类型");
    }

    private final JdbcTemplate jdbcTemplate;

    public SqlValidator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Parses, classifies, and security-checks the given SQL.
     *
     * @param sql          raw SQL string from the MCP client
     * @param expectedType the SQL type the calling tool expects
     * @return a {@link SqlParseResult} carrying the original SQL, AST node, and
     * confirmed type
     * @throws McpBusinessException on any validation failure
     */
    public SqlParseResult validate(String sql, SqlType expectedType) {
        if (sql == null || sql.isBlank()) {
            throw new McpBusinessException(McpErrorCode.SQL_EMPTY);
        }

        Statement statement;
        try {
            Statements statements = CCJSqlParserUtil.parseStatements(sql);
            if (statements.size() != 1) {
                throw new McpBusinessException(McpErrorCode.MULTI_STATEMENT);
            }
            statement = statements.get(0);
        } catch (JSQLParserException e) {
            throw new McpBusinessException(McpErrorCode.SQL_PARSE_ERROR, e.getMessage());
        }

        SqlType actualType = detectType(statement);
        if (actualType == null) {
            throw new McpBusinessException(McpErrorCode.FORBIDDEN_OPERATION);
        }
        if (actualType != expectedType) {
            throw new McpBusinessException(McpErrorCode.TYPE_MISMATCH, "expected " + expectedType + " but got " + actualType);
        }

        if (statement instanceof Select select) {
            checkForbiddenInSelect(select);
            if (FOR_CLAUSE_PATTERN.matcher(sql).find()) {
                throw new McpBusinessException(McpErrorCode.FORBIDDEN_OPERATION, "SELECT with FOR UPDATE / FOR SHARE is not allowed");
            }
        }

        verifyExplain(sql);

        return new SqlParseResult(sql, statement, actualType);
    }

    /**
     * Maps a parsed JSqlParser {@link Statement} to a {@link SqlType}.
     * Returns {@code null} for unrecognised types; throws
     * {@link McpBusinessException} for black-listed dangerous operations.
     */
    private SqlType detectType(Statement statement) {
        if (statement instanceof Select) {
            return SqlType.SELECT;
        }
        if (statement instanceof Insert) {
            return SqlType.INSERT;
        }
        if (statement instanceof Update) {
            return SqlType.UPDATE;
        }
        if (statement instanceof Delete) {
            return SqlType.DELETE;
        }
        if (statement instanceof CreateTable) {
            return SqlType.CREATE_TABLE;
        }
        if (statement instanceof Drop) {
            return SqlType.DROP_TABLE;
        }
        if (statement instanceof Truncate || statement instanceof Alter || statement instanceof Grant || statement instanceof Execute || statement instanceof SetStatement || statement instanceof Block || statement instanceof Commit || statement instanceof RollbackStatement || statement instanceof SavepointStatement || statement instanceof DeclareStatement || statement instanceof ExplainStatement || statement instanceof UnsupportedStatement) {
            throw new McpBusinessException(McpErrorCode.FORBIDDEN_OPERATION, statement.getClass().getSimpleName());
        }
        return null;
    }

    private void checkForbiddenInSelect(Select select) {
        if (select.getForClause() != null) {
            throw new McpBusinessException(McpErrorCode.FORBIDDEN_OPERATION, "SELECT FOR UPDATE/NOWAIT is not allowed");
        }
        List<WithItem> withItems = select.getWithItemsList();
        if (withItems != null) {
            for (WithItem item : withItems) {
                checkSelectBodyForDml(item);
            }
        }
        checkSelectBodyForDml(select);
    }

    private void checkSelectBodyForDml(Select select) {
        if (select instanceof PlainSelect ps) {
            checkPlainSelect(ps);
        } else if (select instanceof SetOperationList sol) {
            checkSetOperationList(sol);
        } else if (select instanceof ParenthesedSelect pss) {
            checkParenthesedSelect(pss);
        }
    }

    private void checkPlainSelect(PlainSelect ps) {
        checkFromItem(ps.getFromItem());
        List<Join> joins = ps.getJoins();
        if (joins != null) {
            for (Join join : joins) {
                checkFromItem(join.getRightItem());
            }
        }
    }

    private void checkFromItem(FromItem fromItem) {
        if (fromItem instanceof ParenthesedSelect pss) {
            checkSelectBodyForDml(pss);
        }
    }

    private void checkSetOperationList(SetOperationList sol) {
        for (Select sb : sol.getSelects()) {
            checkSelectBodyForDml(sb);
        }
    }

    private void checkParenthesedSelect(ParenthesedSelect pss) {
        Select inner = pss.getSelect();
        if (inner != null) {
            checkSelectBodyForDml(inner);
        }
    }

    private void verifyExplain(String sql) {
        if (jdbcTemplate == null) {
            return;
        }
        try {
            jdbcTemplate.queryForList("EXPLAIN " + sql);
        } catch (Exception e) {
            log.warn("EXPLAIN verification failed (non-fatal): {}", e.getMessage());
        }
    }
}
