package com.crudclass.mcpserver.pg.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 国际化消息提供者，根据配置动态切换 MCP 工具描述和参数提示的语言。
 * <p>
 * 从配置读取 {@code crudclass.mcp.tool-locale}（默认 {@code zh}），
 * 设置为 {@code en} 时所有描述切换为英文，适配非中文 LLM。
 *
 * @author CRUDClass
 */
@Component
public class ToolMessages {

    @Value("${crudclass.mcp.tool-locale:zh}")
    private String locale;

    /**
     * 是否启用英文模式。
     *
     * @return 如果 locale 为 "en" 返回 true
     */
    public boolean isEnglish() {
        return "en".equalsIgnoreCase(locale);
    }

    // ---- 查询工具 ----

    public String queryDescription() {
        return isEnglish()
            ? "Execute read-only SELECT query on PostgreSQL. Supports SELECT and WITH (CTE)."
            : "在 PostgreSQL 上执行只读 SELECT 查询。支持 SELECT 和 WITH (CTE)。";
    }

    public String sqlParamDesc() {
        return isEnglish()
            ? "PostgreSQL query statement"
            : "PostgreSQL 查询语句";
    }

    public String limitParamDesc() {
        return isEnglish()
            ? "Maximum rows to return (1-1000, default 100)"
            : "返回的最大行数 (1-1000, 默认 100)";
    }

    public String offsetParamDesc() {
        return isEnglish()
            ? "Number of rows to skip (default 0)"
            : "跳过的行数 (默认 0)";
    }

    // ---- 写入工具 ----

    public String insertDescription() {
        return isEnglish()
            ? "Insert data into a PostgreSQL table. Returns preview on first call; set confirm=true to execute."
            : "向 PostgreSQL 表插入数据。首次返回预览，设置 confirm=true 确认执行。";
    }

    public String updateDescription() {
        return isEnglish()
            ? "Update data in a PostgreSQL table. Returns preview on first call; set confirm=true to execute."
            : "更新 PostgreSQL 表中的数据。首次返回预览，设置 confirm=true 确认执行。";
    }

    public String deleteDescription() {
        return isEnglish()
            ? "Delete data from a PostgreSQL table. Returns preview on first call; set confirm=true to execute."
            : "从 PostgreSQL 表中删除数据。首次返回预览，设置 confirm=true 确认执行。";
    }

    public String ddlDescription() {
        return isEnglish()
            ? "Create or drop a table in PostgreSQL. Returns preview on first call; set confirm=true to execute."
            : "在 PostgreSQL 中创建或删除表。首次返回预览，设置 confirm=true 确认执行。";
    }

    public String batchDescription() {
        return isEnglish()
            ? "Execute multiple SQL statements in a single transaction. Returns preview on first call; set confirm=true to execute."
            : "在单个事务中执行多条 SQL。首次返回预览，设置 confirm=true 确认执行。";
    }

    // ---- 通用参数 ----

    public String confirmParamDesc() {
        return isEnglish()
            ? "Set to true on second call to confirm execution"
            : "二次调用时设为 true 确认执行";
    }

    public String sqlsParamDesc() {
        return isEnglish()
            ? "Array of SQL statements to execute in a single transaction"
            : "要在事务中执行的 SQL 语句数组";
    }
}
