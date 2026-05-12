package com.crudclass.mcpserver.pg.service;

import com.crudclass.mcpserver.pg.enums.SqlType;
import net.sf.jsqlparser.statement.Statement;

/**
 * Immutable container for the result of SQL parsing and validation.
 * <p>
 * Holds the original SQL text, the JSqlParser AST {@link Statement}, and the
 * classified {@link SqlType}. Consumed by {@link SqlExecutor}
 * for execution or preview.
 * @author CRUDClass
 */
public record SqlParseResult(String originalSql, Statement statement, SqlType sqlType) {

}
