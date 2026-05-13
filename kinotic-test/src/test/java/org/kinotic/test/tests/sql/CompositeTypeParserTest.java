package org.kinotic.test.tests.sql;

import jakarta.annotation.PostConstruct;
import org.junit.jupiter.api.Test;
import org.kinotic.sql.domain.Column;
import org.kinotic.sql.domain.ColumnType;
import org.kinotic.sql.domain.MigrationContent;
import org.kinotic.sql.domain.statements.CreateTableStatement;
import org.kinotic.sql.executor.MigrationExecutor;
import org.kinotic.sql.parsers.MigrationParser;
import org.kinotic.test.support.elastic.ElasticTestBase;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Parser-level tests: assert on the Column tree produced by MigrationParser
 * without executing against Elasticsearch.
 */
class CompositeTypeParserTest extends ElasticTestBase {

    @Autowired
    private MigrationParser migrationParser;

    @Autowired
    private MigrationExecutor migrationExecutor;

    @PostConstruct
    void setup() throws Exception {
        migrationExecutor.ensureMigrationIndexExists().get();
    }

    private CreateTableStatement parseCreate(String sql) {
        MigrationContent content = migrationParser.parse(sql);
        return (CreateTableStatement) content.statements().get(0);
    }

    private Column column(CreateTableStatement stmt, String name) {
        return stmt.columns().stream()
                   .filter(c -> c.name().equals(name))
                   .findFirst()
                   .orElseThrow(() -> new AssertionError("Column not found: " + name));
    }

    // ── OBJECT ───────────────────────────────────────────────────────────────

    @Test
    void whenParseObjectColumn_thenColumnTreeCorrect() {
        CreateTableStatement stmt = parseCreate("""
            CREATE TABLE p_obj (
                id KEYWORD,
                address OBJECT (street TEXT, city KEYWORD, zip KEYWORD)
            );
            """);

        Column address = column(stmt, "address");
        assertEquals(ColumnType.OBJECT, address.type());
        assertTrue(address.indexed());
        List<Column> sub = address.subColumns();
        assertEquals(3, sub.size());
        assertEquals("street",  sub.get(0).name()); assertEquals(ColumnType.TEXT,    sub.get(0).type());
        assertEquals("city",    sub.get(1).name()); assertEquals(ColumnType.KEYWORD, sub.get(1).type());
        assertEquals("zip",     sub.get(2).name()); assertEquals(ColumnType.KEYWORD, sub.get(2).type());
    }

    @Test
    void whenParseObjectNotIndexed_thenIndexedFalseAndSubColumnsPresent() {
        CreateTableStatement stmt = parseCreate("""
            CREATE TABLE p_obj_ni (
                id KEYWORD,
                payload OBJECT (raw TEXT, size INTEGER) NOT INDEXED
            );
            """);

        Column payload = column(stmt, "payload");
        assertEquals(ColumnType.OBJECT, payload.type());
        assertFalse(payload.indexed());
        assertEquals(2, payload.subColumns().size());
    }

    // ── NESTED ───────────────────────────────────────────────────────────────

    @Test
    void whenParseNestedColumn_thenColumnTreeCorrect() {
        CreateTableStatement stmt = parseCreate("""
            CREATE TABLE p_nested (
                id KEYWORD,
                tags NESTED (label TEXT, value KEYWORD)
            );
            """);

        Column tags = column(stmt, "tags");
        assertEquals(ColumnType.NESTED, tags.type());
        assertTrue(tags.indexed());
        List<Column> sub = tags.subColumns();
        assertEquals(2, sub.size());
        assertEquals("label", sub.get(0).name()); assertEquals(ColumnType.TEXT,    sub.get(0).type());
        assertEquals("value", sub.get(1).name()); assertEquals(ColumnType.KEYWORD, sub.get(1).type());
    }

    // ── UNION ────────────────────────────────────────────────────────────────

    @Test
    void whenParseUnionColumn_thenVariantsStoredAsObjectSubColumns() {
        CreateTableStatement stmt = parseCreate("""
            CREATE TABLE p_union (
                id KEYWORD,
                item UNION (
                    Book  (kind KEYWORD, title TEXT, isbn KEYWORD),
                    Video (kind KEYWORD, title TEXT, duration INTEGER)
                )
            );
            """);

        Column item = column(stmt, "item");
        assertEquals(ColumnType.UNION, item.type());
        assertTrue(item.indexed());

        List<Column> variants = item.subColumns();
        assertEquals(2, variants.size());

        Column book = variants.get(0);
        assertEquals("Book",        book.name());
        assertEquals(ColumnType.OBJECT, book.type());
        assertEquals(3,             book.subColumns().size());
        assertEquals("kind",  book.subColumns().get(0).name());
        assertEquals("title", book.subColumns().get(1).name());
        assertEquals("isbn",  book.subColumns().get(2).name());

        Column video = variants.get(1);
        assertEquals("Video",       video.name());
        assertEquals(ColumnType.OBJECT, video.type());
        assertEquals(3,             video.subColumns().size());
        assertEquals("duration", video.subColumns().get(2).name());
        assertEquals(ColumnType.INTEGER, video.subColumns().get(2).type());
    }

    @Test
    void whenParseUnionNotIndexed_thenIndexedFalse() {
        CreateTableStatement stmt = parseCreate("""
            CREATE TABLE p_union_ni (
                id KEYWORD,
                item UNION (TypeA (x TEXT)) NOT INDEXED
            );
            """);

        Column item = column(stmt, "item");
        assertEquals(ColumnType.UNION, item.type());
        assertFalse(item.indexed());
        assertEquals(1, item.subColumns().size());
    }

    // ── RECURSIVE NESTING ────────────────────────────────────────────────────

    @Test
    void whenParseObjectInsideObject_thenDeepTreeCorrect() {
        CreateTableStatement stmt = parseCreate("""
            CREATE TABLE p_obj_obj (
                id KEYWORD,
                location OBJECT (
                    label TEXT,
                    coords OBJECT (lat DOUBLE, lon DOUBLE)
                )
            );
            """);

        Column location = column(stmt, "location");
        assertEquals(ColumnType.OBJECT, location.type());

        Column coords = location.subColumns().stream()
                                .filter(c -> c.name().equals("coords"))
                                .findFirst().orElseThrow();
        assertEquals(ColumnType.OBJECT, coords.type());
        assertEquals(2, coords.subColumns().size());
        assertEquals("lat", coords.subColumns().get(0).name());
        assertEquals(ColumnType.DOUBLE, coords.subColumns().get(0).type());
    }

    @Test
    void whenParseObjectInsideNested_thenDeepTreeCorrect() {
        CreateTableStatement stmt = parseCreate("""
            CREATE TABLE p_obj_nested (
                id KEYWORD,
                items NESTED (
                    name TEXT,
                    meta OBJECT (source KEYWORD, score FLOAT)
                )
            );
            """);

        Column items = column(stmt, "items");
        assertEquals(ColumnType.NESTED, items.type());

        Column meta = items.subColumns().stream()
                           .filter(c -> c.name().equals("meta"))
                           .findFirst().orElseThrow();
        assertEquals(ColumnType.OBJECT, meta.type());
        assertEquals(2, meta.subColumns().size());
        assertEquals("source", meta.subColumns().get(0).name());
        assertEquals("score",  meta.subColumns().get(1).name());
    }

    @Test
    void whenParseUnionInsideObject_thenVariantsNested() {
        CreateTableStatement stmt = parseCreate("""
            CREATE TABLE p_union_obj (
                id KEYWORD,
                wrapper OBJECT (
                    ref KEYWORD,
                    item UNION (
                        TypeA (kind KEYWORD, x TEXT),
                        TypeB (kind KEYWORD, y INTEGER)
                    )
                )
            );
            """);

        Column wrapper = column(stmt, "wrapper");
        assertEquals(ColumnType.OBJECT, wrapper.type());

        Column item = wrapper.subColumns().stream()
                             .filter(c -> c.name().equals("item"))
                             .findFirst().orElseThrow();
        assertEquals(ColumnType.UNION, item.type());
        assertEquals(2, item.subColumns().size());
        assertEquals("TypeA", item.subColumns().get(0).name());
        assertEquals("TypeB", item.subColumns().get(1).name());
    }
}
