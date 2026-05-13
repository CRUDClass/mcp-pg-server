package com.crudclass.mcpserver.pg.service;

import java.util.List;
import java.util.Map;

/**
 * @author CRUDClass
 * @date 2026/05/13
 **/
public interface PgService {

    /**
     * 在单个事务中批量执行多条 SQL。
     *
     * @param sqls    SQL 语句列表
     * @param confirm 设为 true 确认执行；null/false 返回预览
     * @return 执行结果（预览模式下返回 actionRequired: "confirm"）
     */
    Map<String, Object> executeBatch(List<String> sqls, Boolean confirm);

    /**
     * 执行只读 SELECT 查询，自动追加 LIMIT/OFFSET 分页。
     *
     * @param sql    SELECT 查询语句
     * @param limit  返回行数上限（默认 100，最大 1000）
     * @param offset 跳过行数（默认 0）
     * @return 查询结果，含 rows、rowCount、hasMore、limit、offset
     */
    Map<String, Object> executeQuery(
            String sql,
            Integer limit,
            Integer offset);

    /**
     * 执行 INSERT 插入语句，两阶段预览/确认模式。
     *
     * @param sql     INSERT 语句
     * @param confirm 设为 true 确认执行
     * @return 执行结果或预览
     */
    Map<String, Object> executeInsert(
            String sql,
            Boolean confirm);

    /**
     * 执行 UPDATE 更新语句，两阶段预览/确认模式。
     *
     * @param sql     UPDATE 语句
     * @param confirm 设为 true 确认执行
     * @return 执行结果或预览
     */
    Map<String, Object> executeUpdate(
            String sql,
            Boolean confirm);

    /**
     * 执行 DDL 语句（CREATE TABLE / DROP TABLE），两阶段预览/确认模式。
     *
     * @param sql     DDL 语句
     * @param confirm 设为 true 确认执行
     * @return 执行结果或预览
     */
    Map<String, Object> executeDdl(String sql, Boolean confirm);

    /**
     * 执行 DELETE 删除语句，两阶段预览/确认模式。
     *
     * @param sql     DELETE 语句
     * @param confirm 设为 true 确认执行
     * @return 执行结果或预览
     */
    Map<String, Object> executeDelete(String sql, Boolean confirm);

    /**
     * 列出 public schema 下所有表及注释。
     *
     * @return 含 tables 列表和 count 计数
     */
    Map<String, Object> listTables();

    /**
     * 查询某张表的完整元数据（列、主键、索引、外键、注释）。
     *
     * @param tableName 表名
     * @return 含 columns、primaryKeys、indexes、foreignKeys、tableComment
     */
    Map<String, Object> describeTable(String tableName);
}
