package com.crudclass.mcpserver.pg.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class McpErrorCodeTest {

    @Test
    void getMessageReturnsChineseForZh() {
        assertEquals("SQL 不能为空", McpErrorCode.SQL_EMPTY.getMessage("zh"));
        assertEquals("SQL 解析失败", McpErrorCode.SQL_PARSE_ERROR.getMessage("zh"));
        assertEquals("不允许执行多条 SQL", McpErrorCode.MULTI_STATEMENT.getMessage("zh"));
        assertEquals("查询超时", McpErrorCode.QUERY_TIMEOUT.getMessage("zh"));
        assertEquals("SQL 执行失败", McpErrorCode.EXECUTION_FAILED.getMessage("zh"));
        assertEquals("SQL 校验失败", McpErrorCode.VALIDATION_FAILED.getMessage("zh"));
    }

    @Test
    void getMessageReturnsEnglishForEn() {
        assertEquals("SQL cannot be empty", McpErrorCode.SQL_EMPTY.getMessage("en"));
        assertEquals("SQL parse error", McpErrorCode.SQL_PARSE_ERROR.getMessage("en"));
        assertEquals("Multiple statements not allowed", McpErrorCode.MULTI_STATEMENT.getMessage("en"));
        assertEquals("Query timeout", McpErrorCode.QUERY_TIMEOUT.getMessage("en"));
        assertEquals("SQL execution failed", McpErrorCode.EXECUTION_FAILED.getMessage("en"));
        assertEquals("SQL validation failed", McpErrorCode.VALIDATION_FAILED.getMessage("en"));
    }

    @Test
    void getMessageDefaultsToChineseForUnknownLocale() {
        assertEquals("SQL 不能为空", McpErrorCode.SQL_EMPTY.getMessage("fr"));
        assertEquals("SQL 不能为空", McpErrorCode.SQL_EMPTY.getMessage(null));
    }

    @Test
    void allCodesHaveBothMessages() {
        for (McpErrorCode code : McpErrorCode.values()) {
            assertNotNull(code.getZhMessage(), code + " zhMessage is null");
            assertNotNull(code.getEnMessage(), code + " enMessage is null");
            assertFalse(code.getZhMessage().isBlank(), code + " zhMessage is blank");
            assertFalse(code.getEnMessage().isBlank(), code + " enMessage is blank");
        }
    }
}
