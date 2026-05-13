package com.crudclass.mcpserver.pg;

import com.crudclass.mcpserver.pg.config.ToolMessages;
import com.crudclass.mcpserver.pg.tool.*;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * Spring Boot entry point for the <b>crudclass</b> PostgreSQL MCP server.
 * <p>
 * Uses Undertow as the embedded web container (Tomcat excluded for macOS SO_LINGER
 * compatibility) and exposes six {@code @Tool}-annotated MCP resources over the
 * STREAMABLE HTTP transport at {@code /api/mcp}.
 * <p>
 * Tool callbacks are registered explicitly via {@link MethodToolCallbackProvider}
 * because the Spring AI 1.1.2 annotation scanner does not auto-detect them in
 * the WebMVC MCP server configuration.
 *
 * @author CRUDClass
 */
@SpringBootApplication
public class McpPgServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(McpPgServerApplication.class, args);
    }

    @Bean
    public ToolCallbackProvider toolCallbackProvider(
            ToolMessages messages,
            QueryTool queryTool, InsertTool insertTool,
            UpdateTool updateTool, DeleteTool deleteTool,
            DdlTool ddlTool, BatchTool batchTool,
            ListTablesTool listTablesTool, DescribeTableTool describeTableTool) {
        return new LocaleAwareToolCallbackProvider(messages,
                queryTool, insertTool, updateTool,
                deleteTool, ddlTool, batchTool,
                listTablesTool, describeTableTool);
    }
}
