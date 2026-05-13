package com.crudclass.mcpserver.pg.tool;

import com.crudclass.mcpserver.pg.service.PgService;
import lombok.AllArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@AllArgsConstructor
public class ListTablesTool {

    private final PgService pgService;

    @Tool(description = "列出 public schema 下所有表名及注释")
    public Map<String, Object> listTables() {
        return pgService.listTables();
    }
}
