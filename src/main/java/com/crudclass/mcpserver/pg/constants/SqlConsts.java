package com.crudclass.mcpserver.pg.constants;

/**
 * @author LiWenBo
 * @date 2026/05/13
 **/
public class SqlConsts {

    public static final String SQL_COLUMNS = """
            SELECT c.column_name         AS name,
                   c.data_type            AS type,
                   c.is_nullable          AS nullable,
                   c.column_default       AS "defaultValue",
                   pg_catalog.col_description(pc.oid, c.ordinal_position::int) AS comment
            FROM information_schema.columns c
            JOIN pg_catalog.pg_class pc ON pc.relname = c.table_name
                AND pc.relnamespace = (SELECT oid FROM pg_catalog.pg_namespace WHERE nspname = 'public')
            WHERE c.table_schema = 'public'
              AND c.table_name = ?
            ORDER BY c.ordinal_position
            """;

    public static final String SQL_PRIMARY_KEYS = """
            SELECT kcu.column_name AS name
            FROM information_schema.table_constraints tc
            JOIN information_schema.key_column_usage kcu
                ON tc.constraint_name = kcu.constraint_name
                AND tc.table_schema = kcu.table_schema
            WHERE tc.constraint_type = 'PRIMARY KEY'
              AND tc.table_schema = 'public'
              AND tc.table_name = ?
            ORDER BY kcu.ordinal_position
            """;

    public static final String SQL_INDEXES = """
            SELECT i.relname       AS name,
                   idx.indisunique AS "unique",
                   (SELECT array_to_string(
                       array_agg(att.attname ORDER BY array_position(idx.indkey::int[], att.attnum)), ', ')
                    FROM pg_attribute att
                    WHERE att.attrelid = t.oid AND att.attnum = ANY(idx.indkey)) AS columns
            FROM pg_index idx
            JOIN pg_class i ON i.oid = idx.indexrelid
            JOIN pg_class t ON t.oid = idx.indrelid
            JOIN pg_namespace ns ON ns.oid = t.relnamespace
            WHERE t.relname = ?
              AND ns.nspname = 'public'
              AND idx.indisprimary = false
            ORDER BY i.relname
            """;

    public static final String SQL_FOREIGN_KEYS = """
            SELECT con.conname     AS name,
                   (SELECT array_to_string(
                       array_agg(att.attname ORDER BY ord.n), ', ')
                    FROM unnest(con.conkey) WITH ORDINALITY AS ord(col, n)
                    JOIN pg_attribute att ON att.attrelid = con.conrelid AND att.attnum = ord.col) AS "sourceColumns",
                   confrel.relname AS "targetTable",
                   (SELECT array_to_string(
                       array_agg(att.attname ORDER BY ord.n), ', ')
                    FROM unnest(con.confkey) WITH ORDINALITY AS ord(col, n)
                    JOIN pg_attribute att ON att.attrelid = con.confrelid AND att.attnum = ord.col) AS "targetColumns"
            FROM pg_constraint con
            JOIN pg_class rel ON rel.oid = con.conrelid
            JOIN pg_class confrel ON confrel.oid = con.confrelid
            JOIN pg_namespace nsp ON nsp.oid = rel.relnamespace
            WHERE rel.relname = ?
              AND nsp.nspname = 'public'
              AND con.contype = 'f'
            """;

    public static final String SQL_TABLE_COMMENT = """
            SELECT pg_catalog.obj_description(pc.oid, 'pg_class') AS "tableComment"
            FROM pg_catalog.pg_class pc
            JOIN pg_catalog.pg_namespace ns ON ns.oid = pc.relnamespace
            WHERE pc.relname = ? AND ns.nspname = 'public'
            """;

    public static final String SQL_TABLE_LIST = """
            SELECT t.tablename AS "tableName",
                   pg_catalog.obj_description(c.oid, 'pg_class') AS "comment"
            FROM pg_catalog.pg_tables t
            JOIN pg_catalog.pg_class c ON c.relname = t.tablename
            JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace AND n.nspname = 'public'
            WHERE t.schemaname = 'public'
            ORDER BY t.tablename
            """;
}
