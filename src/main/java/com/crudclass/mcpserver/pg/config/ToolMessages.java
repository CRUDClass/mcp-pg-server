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

    // ---- 元数据查询工具 ----

    public String listTablesDescription() {
        return isEnglish()
            ? "List all tables in the public schema with their comments"
            : "列出 public schema 下所有表名及注释";
    }

    public String describeTableDescription() {
        return isEnglish()
            ? "Query complete metadata for a table in the public schema (columns, primary keys, indexes, foreign keys, comments)"
            : "查询 public schema 下某张表的完整元数据（列、主键、索引、外键、注释）";
    }

    // ---- 各工具专用 SQL 参数描述 ----

    public String insertSqlParamDesc() {
        return isEnglish()
            ? "PostgreSQL INSERT statement"
            : "PostgreSQL INSERT 语句";
    }

    public String updateSqlParamDesc() {
        return isEnglish()
            ? "PostgreSQL UPDATE statement"
            : "PostgreSQL UPDATE 语句";
    }

    public String deleteSqlParamDesc() {
        return isEnglish()
            ? "PostgreSQL DELETE statement"
            : "PostgreSQL DELETE 语句";
    }

    public String ddlSqlParamDesc() {
        return isEnglish()
            ? "PostgreSQL CREATE TABLE or DROP TABLE statement"
            : "PostgreSQL CREATE TABLE 或 DROP TABLE 语句";
    }

    public String tableNameParamDesc() {
        return isEnglish()
            ? "Table name"
            : "表名";
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

    // ---- 运行时消息 ----

    private static final String ROW_AFFECTED = " row(s) affected";

    public String previewMessage() {
        return isEnglish()
            ? "Preview mode — SQL validated but not executed. Call again with confirm=true to execute."
            : "预览模式，SQL 校验通过，未实际执行。请重新调用并设置 confirm=true 确认执行。";
    }

    public String batchPreviewMessage(int count) {
        return isEnglish()
            ? "Preview mode — " + count + " SQL statement(s) validated but not executed. Call again with confirm=true."
            : "预览模式，共 " + count + " 条 SQL 校验通过，未实际执行。请重新调用并设置 confirm=true。";
    }

    public String selectSuccess(int rows) {
        return isEnglish()
            ? "Query successful, returned " + rows + " row(s)"
            : "查询成功，返回 " + rows + " 行";
    }

    public String insertSuccess(int affected) {
        return isEnglish()
            ? "Insert successful, " + affected + ROW_AFFECTED
            : "插入成功，影响 " + affected + " 行";
    }

    public String updateSuccess(int affected) {
        return isEnglish()
            ? "Update successful, " + affected + ROW_AFFECTED
            : "更新成功，影响 " + affected + " 行";
    }

    public String deleteSuccess(int affected) {
        return isEnglish()
            ? "Delete successful, " + affected + ROW_AFFECTED
            : "删除成功，影响 " + affected + " 行";
    }

    public String createSuccess() {
        return isEnglish()
            ? "Created successfully"
            : "创建成功";
    }

    public String dropSuccess() {
        return isEnglish()
            ? "Dropped successfully"
            : "删除成功";
    }

    public String unsupportedType() {
        return isEnglish()
            ? "Unsupported SQL type"
            : "不支持的 SQL 类型";
    }

    public String ddlOnlySupport() {
        return isEnglish()
            ? "Only CREATE TABLE and DROP TABLE are supported"
            : "仅支持 CREATE TABLE 和 DROP TABLE";
    }

    public String tableNameNotEmpty() {
        return isEnglish()
            ? "tableName cannot be empty"
            : "tableName 不能为空";
    }

    public String illegalTableName(String name) {
        return isEnglish()
            ? "Illegal table name: " + name
            : "非法表名: " + name;
    }
}
