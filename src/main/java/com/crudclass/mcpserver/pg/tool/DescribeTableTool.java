package com.crudclass.mcpserver.pg.tool;

import com.crudclass.mcpserver.pg.service.PgService;
import lombok.AllArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * MCP 工具：查询某张表的完整元数据。
 * <p>
 * 返回内容包括：列信息（含主键标记）、主键列表、索引列表、
 * 外键列表、表注释。委托 {@link PgService#describeTable(String)} 执行。
 *
 * @author CRUDClass
 */
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
