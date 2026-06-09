package com.crudclass.mcpserver.pg.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class ToolMessagesTest {

    private ToolMessages messages;

    @BeforeEach
    void setUp() {
        messages = new ToolMessages();
    }

    private void setLocale(String locale) {
        ReflectionTestUtils.setField(messages, "locale", locale);
    }

    @Test
    void zhQueriesReturnChinese() {
        setLocale("zh");

        assertEquals("列出 public schema 下所有表名及注释", messages.listTablesDescription());
        assertEquals("查询 public schema 下某张表的完整元数据（列、主键、索引、外键、注释）", messages.describeTableDescription());
        assertEquals("预览模式，SQL 校验通过，未实际执行。请重新调用并设置 confirm=true 确认执行。", messages.previewMessage());
        assertEquals("预览模式，共 3 条 SQL 校验通过，未实际执行。请重新调用并设置 confirm=true。", messages.batchPreviewMessage(3));
        assertEquals("查询成功，返回 42 行", messages.selectSuccess(42));
        assertEquals("插入成功，影响 5 行", messages.insertSuccess(5));
        assertEquals("更新成功，影响 3 行", messages.updateSuccess(3));
        assertEquals("删除成功，影响 1 行", messages.deleteSuccess(1));
        assertEquals("创建成功", messages.createSuccess());
        assertEquals("删除成功", messages.dropSuccess());
        assertEquals("不支持的 SQL 类型", messages.unsupportedType());
        assertEquals("仅支持 CREATE TABLE 和 DROP TABLE", messages.ddlOnlySupport());
        assertEquals("tableName 不能为空", messages.tableNameNotEmpty());
        assertEquals("非法表名: my_table", messages.illegalTableName("my_table"));
    }

    @Test
    void enQueriesReturnEnglish() {
        setLocale("en");

        assertTrue(messages.listTablesDescription().startsWith("List all tables"));
        assertTrue(messages.describeTableDescription().startsWith("Query complete metadata"));
        assertEquals("Preview mode — 3 SQL statement(s) validated but not executed. Call again with confirm=true.", messages.batchPreviewMessage(3));
        assertTrue(messages.selectSuccess(42).contains("42"));
        assertTrue(messages.insertSuccess(5).contains("5"));
        assertTrue(messages.illegalTableName("my_table").contains("my_table"));
    }

    @Test
    void paramDescriptionsSwitchByLocale() {
        setLocale("zh");
        assertEquals("PostgreSQL INSERT 语句", messages.insertSqlParamDesc());
        assertEquals("表名", messages.tableNameParamDesc());

        setLocale("en");
        assertEquals("PostgreSQL INSERT statement", messages.insertSqlParamDesc());
        assertEquals("Table name", messages.tableNameParamDesc());

        setLocale("ZH");
        assertEquals("PostgreSQL INSERT 语句", messages.insertSqlParamDesc());
    }

    @Test
    void defaultLocaleIsZh() {
        assertFalse(messages.isEnglish());
    }
}
