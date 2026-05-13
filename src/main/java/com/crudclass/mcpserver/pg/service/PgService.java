package com.crudclass.mcpserver.pg.service;

import java.util.List;
import java.util.Map;

/**
 * @author CRUDClass
 * @date 2026/05/13
 **/
public interface PgService {

    Map<String, Object> executeBatch(List<String> sqls, Boolean confirm);

    Map<String, Object> executeQuery(
            String sql,
            Integer limit,
            Integer offset);

    Map<String, Object> executeInsert(
            String sql,
            Boolean confirm);

    Map<String, Object> executeUpdate(
            String sql,
            Boolean confirm);

    Map<String, Object> executeDdl(String sql, Boolean confirm);

    Map<String, Object> executeDelete(String sql, Boolean confirm);

    Map<String, Object> listTables();

    Map<String, Object> describeTable(String tableName);
}
