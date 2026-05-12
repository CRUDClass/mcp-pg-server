package com.crudclass.mcpserver.pg.service;

import com.crudclass.mcpserver.pg.enums.SqlType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SqlValidatorTest {

    private final SqlValidator validator = new SqlValidator(null);

    @Test
    void shouldAcceptValidSelect() {
        assertDoesNotThrow(() -> validator.validate("SELECT * FROM users", SqlType.SELECT));
    }

    @Test
    void shouldAcceptValidWith() {
        assertDoesNotThrow(() -> validator.validate("WITH cte AS (SELECT 1) SELECT * FROM cte", SqlType.SELECT));
    }

    @Test
    void shouldAcceptInsert() {
        assertDoesNotThrow(() -> validator.validate("INSERT INTO users (name) VALUES ('alice')", SqlType.INSERT));
    }

    @Test
    void shouldAcceptUpdate() {
        assertDoesNotThrow(() -> validator.validate("UPDATE users SET name = 'bob' WHERE id = 1", SqlType.UPDATE));
    }

    @Test
    void shouldAcceptDelete() {
        assertDoesNotThrow(() -> validator.validate("DELETE FROM users WHERE id = 1", SqlType.DELETE));
    }

    @Test
    void shouldAcceptCreateTable() {
        assertDoesNotThrow(() -> validator.validate("CREATE TABLE users (id SERIAL PRIMARY KEY)", SqlType.CREATE_TABLE));
    }

    @Test
    void shouldAcceptDropTable() {
        assertDoesNotThrow(() -> validator.validate("DROP TABLE users", SqlType.DROP_TABLE));
    }

    @Test
    void shouldRejectEmptySql() {
        RuntimeException e = assertThrows(RuntimeException.class,
                () -> validator.validate("", SqlType.SELECT));
        assertTrue(e.getMessage().contains("不能为空"));
    }

    @Test
    void shouldRejectNullSql() {
        RuntimeException e = assertThrows(RuntimeException.class,
                () -> validator.validate(null, SqlType.SELECT));
        assertTrue(e.getMessage().contains("不能为空"));
    }

    @Test
    void shouldRejectMultiStatement() {
        RuntimeException e = assertThrows(RuntimeException.class,
                () -> validator.validate("SELECT 1; DROP TABLE users", SqlType.SELECT));
        assertTrue(e.getMessage().contains("多条"));
    }

    @Test
    void shouldRejectWrongType_InsertAsSelect() {
        RuntimeException e = assertThrows(RuntimeException.class,
                () -> validator.validate("INSERT INTO users VALUES (1)", SqlType.SELECT));
        assertTrue(e.getMessage().contains("type mismatch") || e.getMessage().contains("不匹配"));
    }

    @Test
    void shouldAcceptSqlWithComments() {
        assertDoesNotThrow(() -> validator.validate("SELECT * FROM users -- this is a comment", SqlType.SELECT));
    }

    @Test
    void shouldAcceptSqlWithBlockComments() {
        assertDoesNotThrow(() -> validator.validate("SELECT /* inline */ * FROM users", SqlType.SELECT));
    }

    @Test
    void shouldRejectForUpdate() {
        RuntimeException e = assertThrows(RuntimeException.class,
                () -> validator.validate("SELECT * FROM t FOR UPDATE", SqlType.SELECT));
        assertTrue(e.getMessage().contains("FORBIDDEN") || e.getMessage().contains("FOR UPDATE"));
    }

    @Test
    void shouldRejectTruncate() {
        RuntimeException e = assertThrows(RuntimeException.class,
                () -> validator.validate("TRUNCATE TABLE t", SqlType.SELECT));
        assertTrue(e.getMessage().contains("FORBIDDEN") || e.getMessage().contains("Truncate"));
    }

    @Test
    void shouldRejectAlter() {
        RuntimeException e = assertThrows(RuntimeException.class,
                () -> validator.validate("ALTER TABLE t ADD COLUMN x INT", SqlType.SELECT));
        assertTrue(e.getMessage().contains("FORBIDDEN") || e.getMessage().contains("Alter"));
    }

    @Test
    void shouldReturnSqlParseResult() {
        SqlParseResult r = validator.validate("SELECT * FROM t", SqlType.SELECT);
        assertNotNull(r);
        assertEquals(SqlType.SELECT, r.sqlType());
        assertEquals("SELECT * FROM t", r.originalSql());
    }

    @Test
    void shouldRejectWritableCteAtParseTime() {
        assertThrows(RuntimeException.class, () ->
                validator.validate("WITH d AS (DELETE FROM t RETURNING *) SELECT * FROM d",
                        SqlType.SELECT));
    }

    @Test
    void shouldRejectSelectForNoWait() {
        RuntimeException e = assertThrows(RuntimeException.class,
                () -> validator.validate("SELECT * FROM t FOR NO KEY UPDATE NOWAIT", SqlType.SELECT));
        assertTrue(e.getMessage().contains("FORBIDDEN") || e.getMessage().contains("FOR UPDATE"));
    }

    @Test
    void shouldAcceptUnionSelect() {
        assertDoesNotThrow(() ->
                validator.validate("SELECT id FROM t UNION SELECT id FROM t2", SqlType.SELECT));
    }

    @Test
    void shouldAcceptSubSelectInFrom() {
        assertDoesNotThrow(() ->
                validator.validate("SELECT * FROM (SELECT * FROM t) AS sub", SqlType.SELECT));
    }

    @Test
    void shouldAcceptWithCteUnion() {
        assertDoesNotThrow(() ->
                validator.validate("WITH a AS (SELECT id FROM t), b AS (SELECT id FROM t2) SELECT * FROM a UNION SELECT * FROM b",
                        SqlType.SELECT));
    }
}
