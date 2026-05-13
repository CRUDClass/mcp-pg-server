package com.crudclass.mcpserver.pg.tool;

import com.crudclass.mcpserver.pg.config.ToolMessages;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Wraps {@link MethodToolCallbackProvider} to produce locale-aware
 * {@link ToolDefinition} instances with descriptions and parameter
 * descriptions sourced from {@link ToolMessages}.
 * <p>
 * The original {@code @Tool} annotations on the 8 tool classes are
 * left unchanged — this provider replaces the metadata at runtime.
 *
 * @author CRUDClass
 */
public class LocaleAwareToolCallbackProvider implements ToolCallbackProvider {

    private final ToolMessages messages;
    private final MethodToolCallbackProvider delegate;

    private static final String K_SQL = "sql";
    private static final String K_CONFIRM = "confirm";
    private static final String T_STRING = "string";
    private static final String T_INTEGER = "integer";
    private static final String T_BOOLEAN = "boolean";
    private static final String T_ARRAY = "array";

    private static final Map<String, List<ParamDef>> PARAMS = Map.of(
        "executeQuery", List.of(
            new ParamDef(K_SQL, T_STRING, true),
            new ParamDef("limit", T_INTEGER, false),
            new ParamDef("offset", T_INTEGER, false)
        ),
        "executeInsert", List.of(
            new ParamDef(K_SQL, T_STRING, true),
            new ParamDef(K_CONFIRM, T_BOOLEAN, false)
        ),
        "executeUpdate", List.of(
            new ParamDef(K_SQL, T_STRING, true),
            new ParamDef(K_CONFIRM, T_BOOLEAN, false)
        ),
        "executeDelete", List.of(
            new ParamDef(K_SQL, T_STRING, true),
            new ParamDef(K_CONFIRM, T_BOOLEAN, false)
        ),
        "executeDdl", List.of(
            new ParamDef(K_SQL, T_STRING, true),
            new ParamDef(K_CONFIRM, T_BOOLEAN, false)
        ),
        "executeBatch", List.of(
            new ParamDef("sqls", T_ARRAY, true),
            new ParamDef(K_CONFIRM, T_BOOLEAN, false)
        ),
        "listTables", List.of(),
        "describeTable", List.of(
            new ParamDef("tableName", T_STRING, true)
        )
    );

    public LocaleAwareToolCallbackProvider(ToolMessages messages, Object... toolObjects) {
        this.messages = messages;
        this.delegate = MethodToolCallbackProvider.builder()
                .toolObjects(toolObjects)
                .build();
    }

    @Override
    public ToolCallback[] getToolCallbacks() {
        return Arrays.stream(delegate.getToolCallbacks())
                .map(this::wrap)
                .toArray(ToolCallback[]::new);
    }

    private ToolCallback wrap(ToolCallback original) {
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return buildLocalizedDef(original.getToolDefinition());
            }

            @Override
            public String call(String toolInput) {
                return original.call(toolInput);
            }
        };
    }

    private ToolDefinition buildLocalizedDef(ToolDefinition original) {
        String name = original.name();
        return ToolDefinition.builder()
                .name(name)
                .description(descriptionFor(name))
                .inputSchema(buildSchema(name))
                .build();
    }

    private String descriptionFor(String toolName) {
        return switch (toolName) {
            case "executeQuery" -> messages.queryDescription();
            case "executeInsert" -> messages.insertDescription();
            case "executeUpdate" -> messages.updateDescription();
            case "executeDelete" -> messages.deleteDescription();
            case "executeDdl" -> messages.ddlDescription();
            case "executeBatch" -> messages.batchDescription();
            case "listTables" -> messages.listTablesDescription();
            case "describeTable" -> messages.describeTableDescription();
            default -> "";
        };
    }

    private String buildSchema(String toolName) {
        List<ParamDef> params = PARAMS.get(toolName);
        if (params == null || params.isEmpty()) {
            return "{\"type\":\"object\",\"properties\":{}}";
        }
        return SchemaBuilder.build(params, paramDescriptionsFor(toolName));
    }

    private Map<String, String> paramDescriptionsFor(String toolName) {
        return switch (toolName) {
            case "executeQuery" -> Map.of(
                K_SQL, messages.sqlParamDesc(),
                "limit", messages.limitParamDesc(),
                "offset", messages.offsetParamDesc()
            );
            case "executeInsert" -> Map.of(
                K_SQL, messages.insertSqlParamDesc(),
                K_CONFIRM, messages.confirmParamDesc()
            );
            case "executeUpdate" -> Map.of(
                K_SQL, messages.updateSqlParamDesc(),
                K_CONFIRM, messages.confirmParamDesc()
            );
            case "executeDelete" -> Map.of(
                K_SQL, messages.deleteSqlParamDesc(),
                K_CONFIRM, messages.confirmParamDesc()
            );
            case "executeDdl" -> Map.of(
                K_SQL, messages.ddlSqlParamDesc(),
                K_CONFIRM, messages.confirmParamDesc()
            );
            case "executeBatch" -> Map.of(
                "sqls", messages.sqlsParamDesc(),
                K_CONFIRM, messages.confirmParamDesc()
            );
            case "describeTable" -> Map.of(
                "tableName", messages.tableNameParamDesc()
            );
            default -> Map.of();
        };
    }

    record ParamDef(String name, String type, boolean required) {}

    static final class SchemaBuilder {

        private SchemaBuilder() {
            throw new UnsupportedOperationException("Utility class");
        }

        static String build(List<ParamDef> params, Map<String, String> descriptions) {
            StringBuilder sb = new StringBuilder();
            sb.append("{\"type\":\"object\",\"properties\":{");
            List<String> required = new ArrayList<>();

            boolean first = true;
            for (ParamDef p : params) {
                if (!first) sb.append(",");
                sb.append("\"").append(p.name()).append("\":{");
                sb.append("\"type\":\"").append(toJsonType(p.type())).append("\"");
                if (p.type().equals("array")) {
                    sb.append(",\"items\":{\"type\":\"string\"}");
                }
                sb.append(",\"description\":\"").append(escape(descriptions.get(p.name()))).append("\"");
                sb.append("}");
                if (p.required()) required.add(p.name());
                first = false;
            }
            sb.append("}");
            if (!required.isEmpty()) {
                sb.append(",\"required\":[");
                first = true;
                for (String r : required) {
                    if (!first) sb.append(",");
                    sb.append("\"").append(r).append("\"");
                    first = false;
                }
                sb.append("]");
            }
            sb.append("}");
            return sb.toString();
        }

        private static String toJsonType(String type) {
            return switch (type) {
                case "boolean" -> "boolean";
                case "integer" -> "integer";
                case "array" -> "array";
                default -> "string";
            };
        }

        private static String escape(String s) {
            if (s == null) return "";
            return s.replace("\\", "\\\\").replace("\"", "\\\"");
        }
    }
}
