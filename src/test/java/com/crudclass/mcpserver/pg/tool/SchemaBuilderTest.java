package com.crudclass.mcpserver.pg.tool;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SchemaBuilderTest {

    @Test
    void buildsSchemaWithRequiredAndOptional() {
        List<LocaleAwareToolCallbackProvider.ParamDef> params = List.of(
            new LocaleAwareToolCallbackProvider.ParamDef("sql", "string", true),
            new LocaleAwareToolCallbackProvider.ParamDef("limit", "integer", false)
        );
        Map<String, String> descs = Map.of(
            "sql", "SQL statement",
            "limit", "Max rows"
        );

        String schema = LocaleAwareToolCallbackProvider.SchemaBuilder.build(params, descs);

        assertTrue(schema.contains("\"type\":\"object\""));
        assertTrue(schema.contains("\"sql\":"));
        assertTrue(schema.contains("\"limit\":"));
        assertTrue(schema.contains("\"required\":[\"sql\"]"));
        assertTrue(schema.contains("\"type\":\"string\""));
        assertTrue(schema.contains("\"type\":\"integer\""));
        assertTrue(schema.contains("\"description\":\"SQL statement\""));
        assertTrue(schema.contains("\"description\":\"Max rows\""));
    }

    @Test
    void buildsSchemaWithNoRequired() {
        List<LocaleAwareToolCallbackProvider.ParamDef> params = List.of(
            new LocaleAwareToolCallbackProvider.ParamDef("confirm", "boolean", false)
        );
        Map<String, String> descs = Map.of("confirm", "Confirm execution");

        String schema = LocaleAwareToolCallbackProvider.SchemaBuilder.build(params, descs);

        assertTrue(schema.contains("\"type\":\"boolean\""));
        assertFalse(schema.contains("\"required\""));
    }

    @Test
    void buildsSchemaWithArrayType() {
        List<LocaleAwareToolCallbackProvider.ParamDef> params = List.of(
            new LocaleAwareToolCallbackProvider.ParamDef("sqls", "array", true)
        );
        Map<String, String> descs = Map.of("sqls", "SQL array");

        String schema = LocaleAwareToolCallbackProvider.SchemaBuilder.build(params, descs);

        assertTrue(schema.contains("\"type\":\"array\""));
        assertTrue(schema.contains("\"items\":{\"type\":\"string\"}"));
        assertTrue(schema.contains("\"required\":[\"sqls\"]"));
    }

    @Test
    void buildsEmptySchemaForNoParams() {
        String schema = LocaleAwareToolCallbackProvider.SchemaBuilder.build(List.of(), Map.of());
        assertEquals("{\"type\":\"object\",\"properties\":{}}", schema);
    }

    @Test
    void escapesQuotesInDescriptions() {
        List<LocaleAwareToolCallbackProvider.ParamDef> params = List.of(
            new LocaleAwareToolCallbackProvider.ParamDef("x", "string", true)
        );
        Map<String, String> descs = Map.of("x", "test \"quoted\" value");

        String schema = LocaleAwareToolCallbackProvider.SchemaBuilder.build(params, descs);

        assertTrue(schema.contains("test \\\"quoted\\\" value"));
    }
}
