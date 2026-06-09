package com.crudclass.mcpserver.pg.service;

import com.crudclass.mcpserver.pg.config.ToolMessages;
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
 * SQL 验证引擎，结合 JSqlParser AST 分析、正则行锁检测和 PostgreSQL EXPLAIN 验证。
 *
 * <h3>三层验证</h3>
 * <ol>
 *   <li><b>JSqlParser 解析</b> — 确定语句类型、检测多语句、拒绝可写 CTE 和危险操作
 *       （TRUNCATE、ALTER、GRANT、EXECUTE、SET、BLOCK、COMMIT 等）</li>
 *   <li><b>正则检测</b> — 扫描 {@code FOR UPDATE / FOR SHARE} 子句，
 *       JSqlParser 5.0 不识别这些 PG 扩展语法</li>
 *   <li><b>EXPLAIN 握手</b> — 非致命性验证；发送 {@code EXPLAIN &lt;sql&gt;}
 *       到 PostgreSQL，失败时记录警告但不阻断请求</li>
 * </ol>
 *
 * <p>使用 {@code instanceof} 黑名单匹配所有已知的危险 JSqlParser
 * {@link Statement} 类型，防止纯文本验证可能被绕过的安全问题。
 *
 * @author CRUDClass
 */
@Component
public class SqlValidator {

    private static final Logger log = LoggerFactory.getLogger(SqlValidator.class);
    // 匹配 FOR UPDATE / FOR SHARE 等行锁子句，JSqlParser 5.0 不识别这些 PG 扩展语法
    private static final Pattern FOR_CLAUSE_PATTERN = Pattern.compile("\\bFOR\\s+(NO\\s+KEY\\s+)?(UPDATE|SHARE|KEY\\s+SHARE)\\b", Pattern.CASE_INSENSITIVE);

    /** 前缀 → SQL 类型快速映射表，用于提前判断语句类别 */
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

    /**
     * 通过 SQL 前缀快速检测语句类型，不依赖 JSqlParser。
     * 用于在完整验证之前进行初步分类。
     *
     * @param sql 原始 SQL 字符串
     * @return 对应的 SqlType
     * @throws McpBusinessException 如果前缀不匹配任何已知类型
     */
    public SqlType quickDetectType(String sql) {
        String upper = sql.trim().toUpperCase().replaceAll("\\s+", " ");
        for (var entry : PREFIX_TYPE_MAP.entrySet()) {
            if (upper.startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        throw new McpBusinessException(McpErrorCode.FORBIDDEN_OPERATION,
                safeUnsupportedType(), locale());
    }

    private final JdbcTemplate jdbcTemplate;
    private final ToolMessages messages;

    public SqlValidator(JdbcTemplate jdbcTemplate, ToolMessages messages) {
        this.jdbcTemplate = jdbcTemplate;
        this.messages = messages;
    }

    private String locale() {
        return (messages != null && messages.isEnglish()) ? "en" : "zh";
    }

    private String safeUnsupportedType() {
        return messages != null ? messages.unsupportedType() : "不支持的 SQL 类型";
    }

    /**
     * 完整的 SQL 验证流程。
     */
    public SqlParseResult validate(String sql, SqlType expectedType) {
        if (sql == null || sql.isBlank()) {
            throw new McpBusinessException(McpErrorCode.SQL_EMPTY);
        }

        Statement statement = parseStatement(sql);
        SqlType actualType = detectType(statement);
        if (actualType == null) {
            throw new McpBusinessException(McpErrorCode.FORBIDDEN_OPERATION);
        }
        if (actualType != expectedType) {
            throw new McpBusinessException(McpErrorCode.TYPE_MISMATCH,
                    "expected " + expectedType + " but got " + actualType, locale());
        }

        checkSelectSecurity(sql, statement);
        verifyExplain(sql);

        return new SqlParseResult(sql, statement, actualType);
    }

    private Statement parseStatement(String sql) {
        try {
            Statements statements = CCJSqlParserUtil.parseStatements(sql);
            if (statements.size() != 1) {
                throw new McpBusinessException(McpErrorCode.MULTI_STATEMENT);
            }
            return statements.get(0);
        } catch (JSQLParserException e) {
            throw new McpBusinessException(McpErrorCode.SQL_PARSE_ERROR, e.getMessage());
        }
    }

    private void checkSelectSecurity(String sql, Statement statement) {
        if (!(statement instanceof Select select)) {
            return;
        }
        if (select.getForClause() != null) {
            throw new McpBusinessException(McpErrorCode.FORBIDDEN_OPERATION,
                    "SELECT FOR UPDATE/NOWAIT is not allowed", locale());
        }
        checkForbiddenInSelect(select);
        if (FOR_CLAUSE_PATTERN.matcher(sql).find()) {
            throw new McpBusinessException(McpErrorCode.FORBIDDEN_OPERATION,
                    "SELECT with FOR UPDATE / FOR SHARE is not allowed", locale());
        }
    }

    /**
     * 将 JSqlParser 解析后的 {@link Statement} 映射到 {@link SqlType}。
     * <p>
     * 对未知类型返回 null；对列入黑名单的危险操作直接抛出异常。
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
        // 黑名单检查 —— 以下类型一律禁止
        if (statement instanceof Truncate || statement instanceof Alter || statement instanceof Grant || statement instanceof Execute || statement instanceof SetStatement || statement instanceof Block || statement instanceof Commit || statement instanceof RollbackStatement || statement instanceof SavepointStatement || statement instanceof DeclareStatement || statement instanceof ExplainStatement || statement instanceof UnsupportedStatement) {
            throw new McpBusinessException(McpErrorCode.FORBIDDEN_OPERATION,
                    statement.getClass().getSimpleName(), locale());
        }
        return null;
    }

    /**
     * 检查 SELECT 中的禁止项：FOR UPDATE 子句和可写 CTE。
     */
    private void checkForbiddenInSelect(Select select) {
        List<WithItem> withItems = select.getWithItemsList();
        if (withItems != null) {
            for (WithItem item : withItems) {
                checkSelectBodyForDml(item);
            }
        }
        // 递归检查 SELECT 主查询体
        checkSelectBodyForDml(select);
    }

    /**
     * 根据 Select 的具体子类型分派到对应的检查方法。
     */
    private void checkSelectBodyForDml(Select select) {
        if (select instanceof PlainSelect ps) {
            checkPlainSelect(ps);
        } else if (select instanceof SetOperationList sol) {
            checkSetOperationList(sol);
        } else if (select instanceof ParenthesedSelect pss) {
            checkParenthesedSelect(pss);
        }
    }

    /**
     * 遍历 PlainSelect 的 FROM 项和 JOIN 项，递归检查子查询。
     */
    private void checkPlainSelect(PlainSelect ps) {
        checkFromItem(ps.getFromItem());
        List<Join> joins = ps.getJoins();
        if (joins != null) {
            for (Join join : joins) {
                checkFromItem(join.getRightItem());
            }
        }
    }

    /**
     * 如果 FROM 项是子查询，递归进入检查。
     */
    private void checkFromItem(FromItem fromItem) {
        if (fromItem instanceof ParenthesedSelect pss) {
            checkSelectBodyForDml(pss);
        }
    }

    /**
     * 遍历 UNION/INTERSECT/EXCEPT 组合中的每个 SELECT。
     */
    private void checkSetOperationList(SetOperationList sol) {
        for (Select sb : sol.getSelects()) {
            checkSelectBodyForDml(sb);
        }
    }

    /**
     * 递归进入括号包裹的子查询。
     */
    private void checkParenthesedSelect(ParenthesedSelect pss) {
        Select inner = pss.getSelect();
        if (inner != null) {
            checkSelectBodyForDml(inner);
        }
    }

    /**
     * 非致命性 EXPLAIN 验证。
     * <p>
     * 向 PostgreSQL 发送 {@code EXPLAIN <sql>} 命令，
     * 如果 DataSource 不可用（jdbcTemplate 为 null）则跳过。
     * 执行失败只记录 WARNING 日志，不阻断请求。
     */
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
