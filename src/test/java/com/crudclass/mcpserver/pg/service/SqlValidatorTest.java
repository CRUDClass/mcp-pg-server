package com.crudclass.mcpserver.pg.service;

import com.crudclass.mcpserver.pg.enums.SqlType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link SqlValidator} 单元测试，覆盖 9 类验证场景。
 * <p>
 * 使用 {@code null} JdbcTemplate 构造验证器，跳过 EXPLAIN 握手层，
 * 因此无需 PostgreSQL 连接即可运行全部测试。
 *
 * <ul>
 *   <li>合法 SQL：SELECT / WITH / INSERT / UPDATE / DELETE / CREATE TABLE / DROP TABLE</li>
 *   <li>空/多语句拒绝：空 SQL、null SQL、分号分隔的多语句</li>
 *   <li>类型不匹配：用 SELECT 工具执行 INSERT</li>
 *   <li>注释容忍：行注释（--）、块注释（&#47;*）</li>
 *   <li>行锁拒绝：FOR UPDATE / FOR SHARE / FOR NO KEY UPDATE</li>
 *   <li>危险操作拒绝：TRUNCATE / ALTER</li>
 *   <li>结果校验：SqlParseResult 字段完整</li>
 *   <li>可写 CTE 拒绝：WITH 中包含 DELETE</li>
 *   <li>复杂查询：UNION / 子查询 / 多 CTE</li>
 * </ul>
 *
 * @author CRUDClass
 */
class SqlValidatorTest {

    private final SqlValidator validator = new SqlValidator(null, null);

    /** 合法 SELECT 应通过验证 */
    @Test
    void shouldAcceptValidSelect() {
        assertDoesNotThrow(() -> validator.validate("SELECT * FROM users", SqlType.SELECT));
    }

    /** WITH CTE 查询应通过验证 */
    @Test
    void shouldAcceptValidWith() {
        assertDoesNotThrow(() -> validator.validate("WITH cte AS (SELECT 1) SELECT * FROM cte", SqlType.SELECT));
    }

    /** 合法 INSERT 应通过验证 */
    @Test
    void shouldAcceptInsert() {
        assertDoesNotThrow(() -> validator.validate("INSERT INTO users (name) VALUES ('alice')", SqlType.INSERT));
    }

    /** 合法 UPDATE 应通过验证 */
    @Test
    void shouldAcceptUpdate() {
        assertDoesNotThrow(() -> validator.validate("UPDATE users SET name = 'bob' WHERE id = 1", SqlType.UPDATE));
    }

    /** 合法 DELETE 应通过验证 */
    @Test
    void shouldAcceptDelete() {
        assertDoesNotThrow(() -> validator.validate("DELETE FROM users WHERE id = 1", SqlType.DELETE));
    }

    /** 合法 CREATE TABLE 应通过验证 */
    @Test
    void shouldAcceptCreateTable() {
        assertDoesNotThrow(() -> validator.validate("CREATE TABLE users (id SERIAL PRIMARY KEY)", SqlType.CREATE_TABLE));
    }

    /** 合法 DROP TABLE 应通过验证 */
    @Test
    void shouldAcceptDropTable() {
        assertDoesNotThrow(() -> validator.validate("DROP TABLE users", SqlType.DROP_TABLE));
    }

    /** 空字符串应被拒绝 */
    @Test
    void shouldRejectEmptySql() {
        RuntimeException e = assertThrows(RuntimeException.class,
                () -> validator.validate("", SqlType.SELECT));
        assertTrue(e.getMessage().contains("不能为空"));
    }

    /** null SQL 应被拒绝 */
    @Test
    void shouldRejectNullSql() {
        RuntimeException e = assertThrows(RuntimeException.class,
                () -> validator.validate(null, SqlType.SELECT));
        assertTrue(e.getMessage().contains("不能为空"));
    }

    /** 分号分隔的多条 SQL 应被拒绝 */
    @Test
    void shouldRejectMultiStatement() {
        RuntimeException e = assertThrows(RuntimeException.class,
                () -> validator.validate("SELECT 1; DROP TABLE users", SqlType.SELECT));
        assertTrue(e.getMessage().contains("多条"));
    }

    /** INSERT 语句用 SELECT 类型调用应报类型不匹配 */
    @Test
    void shouldRejectWrongType_InsertAsSelect() {
        RuntimeException e = assertThrows(RuntimeException.class,
                () -> validator.validate("INSERT INTO users VALUES (1)", SqlType.SELECT));
        assertTrue(e.getMessage().contains("type mismatch") || e.getMessage().contains("不匹配"));
    }

    /** 含行注释的 SQL 应正常通过（注释被容忍） */
    @Test
    void shouldAcceptSqlWithComments() {
        assertDoesNotThrow(() -> validator.validate("SELECT * FROM users -- this is a comment", SqlType.SELECT));
    }

    /** 含块注释的 SQL 应正常通过 */
    @Test
    void shouldAcceptSqlWithBlockComments() {
        assertDoesNotThrow(() -> validator.validate("SELECT /* inline */ * FROM users", SqlType.SELECT));
    }

    /** SELECT ... FOR UPDATE 行锁应被拒绝 */
    @Test
    void shouldRejectForUpdate() {
        RuntimeException e = assertThrows(RuntimeException.class,
                () -> validator.validate("SELECT * FROM t FOR UPDATE", SqlType.SELECT));
        assertTrue(e.getMessage().contains("FORBIDDEN") || e.getMessage().contains("FOR UPDATE"));
    }

    /** TRUNCATE 危险操作应被拒绝 */
    @Test
    void shouldRejectTruncate() {
        RuntimeException e = assertThrows(RuntimeException.class,
                () -> validator.validate("TRUNCATE TABLE t", SqlType.SELECT));
        assertTrue(e.getMessage().contains("FORBIDDEN") || e.getMessage().contains("Truncate"));
    }

    /** ALTER 危险操作应被拒绝 */
    @Test
    void shouldRejectAlter() {
        RuntimeException e = assertThrows(RuntimeException.class,
                () -> validator.validate("ALTER TABLE t ADD COLUMN x INT", SqlType.SELECT));
        assertTrue(e.getMessage().contains("FORBIDDEN") || e.getMessage().contains("Alter"));
    }

    /** 验证返回的 SqlParseResult 字段完整 */
    @Test
    void shouldReturnSqlParseResult() {
        SqlParseResult r = validator.validate("SELECT * FROM t", SqlType.SELECT);
        assertNotNull(r);
        assertEquals(SqlType.SELECT, r.sqlType());
        assertEquals("SELECT * FROM t", r.originalSql());
    }

    /** WITH 中包含 DELETE 的可写 CTE 应被拒绝 */
    @Test
    void shouldRejectWritableCteAtParseTime() {
        assertThrows(RuntimeException.class, () ->
                validator.validate("WITH d AS (DELETE FROM t RETURNING *) SELECT * FROM d",
                        SqlType.SELECT));
    }

    /** FOR NO KEY UPDATE NOWAIT 行锁变体应被拒绝 */
    @Test
    void shouldRejectSelectForNoWait() {
        RuntimeException e = assertThrows(RuntimeException.class,
                () -> validator.validate("SELECT * FROM t FOR NO KEY UPDATE NOWAIT", SqlType.SELECT));
        assertTrue(e.getMessage().contains("FORBIDDEN") || e.getMessage().contains("FOR UPDATE"));
    }

    /** UNION 复合查询应通过验证 */
    @Test
    void shouldAcceptUnionSelect() {
        assertDoesNotThrow(() ->
                validator.validate("SELECT id FROM t UNION SELECT id FROM t2", SqlType.SELECT));
    }

    /** FROM 子句中的子查询应通过验证 */
    @Test
    void shouldAcceptSubSelectInFrom() {
        assertDoesNotThrow(() ->
                validator.validate("SELECT * FROM (SELECT * FROM t) AS sub", SqlType.SELECT));
    }

    /** 多 CTE + UNION 复杂查询应通过验证 */
    @Test
    void shouldAcceptWithCteUnion() {
        assertDoesNotThrow(() ->
                validator.validate("WITH a AS (SELECT id FROM t), b AS (SELECT id FROM t2) SELECT * FROM a UNION SELECT * FROM b",
                        SqlType.SELECT));
    }
}
