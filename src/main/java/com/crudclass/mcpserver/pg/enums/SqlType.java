package com.crudclass.mcpserver.pg.enums;

/**
 * SQL statement type classification.
 *
 * @author CRUDClass
 */
public enum SqlType {
    /**
     * SELECT 查询
     */
    SELECT,
    /**
     * INSERT 插入
     */
    INSERT,
    /**
     * UPDATE 更新
     */
    UPDATE,
    /**
     * DELETE 删除
     */
    DELETE,
    /**
     * CREATE TABLE 创建表
     */
    CREATE_TABLE,
    /**
     * DROP TABLE 删除表
     */
    DROP_TABLE
}
