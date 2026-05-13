package com.crudclass.mcpserver.pg.tool;

import com.crudclass.mcpserver.pg.service.PgService;
import lombok.AllArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * MCP 工具：列出 public schema 下所有用户表及注释。
 * <p>
 * 直接委托 {@link PgService#listTables()} 查询系统目录表，
 * 返回表名和表注释的列表。
 *
 * @author CRUDClass
 */
@Component
@AllArgsConstructor
public class ListTablesTool {

    private final PgService pgService;

    @Tool(description = "列出 public schema 下所有表名及注释")
    public Map<String, Object> listTables() {
        return pgService.listTables();
    }
}
