package com.crudclass.mcpserver.pg.tool;

import com.crudclass.mcpserver.pg.service.PgService;
import lombok.AllArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@AllArgsConstructor
public class DescribeTableTool {

    private final PgService service;

    @Tool(description = "查询 public schema 下某张表的完整元数据（列、主键、索引、外键、注释）")
    public Map<String, Object> describeTable(
            @ToolParam(description = "表名") String tableName) {
        return service.describeTable(tableName);
    }

}
