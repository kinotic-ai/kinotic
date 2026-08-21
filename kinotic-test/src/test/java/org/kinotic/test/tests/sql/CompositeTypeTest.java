package org.kinotic.test.tests.sql;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch.core.GetResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.indices.GetMappingResponse;
import jakarta.annotation.PostConstruct;
import org.junit.jupiter.api.Test;
import org.kinotic.sql.domain.Migration;
import org.kinotic.sql.domain.MigrationContent;
import org.kinotic.sql.executor.MigrationExecutor;
import org.kinotic.sql.executor.TypeMapper;
import org.kinotic.sql.parsers.MigrationParser;
import org.kinotic.test.support.kinotic.KinoticTestBase;
import org.springframework.beans.factory.annotation.Autowired;

import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import org.kinotic.sql.domain.statements.CreateTableStatement;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

class CompositeTypeTest extends KinoticTestBase {

    @Autowired
    private ElasticsearchAsyncClient asyncClient;

    @Autowired
    private ElasticsearchClient client;

    @Autowired
    private MigrationExecutor migrationExecutor;

    @Autowired
    private MigrationParser migrationParser;

    static class TestMigration implements Migration {
        private final Integer version;
        private final String name;
        private final String sql;
        private final MigrationParser parser;
        private MigrationContent content;

        TestMigration(Integer version, String name, String sql, MigrationParser parser) {
            this.version = version;
            this.name = name;
            this.sql = sql;
            this.parser = parser;
        }

        @Override public Integer getVersion() { return version; }
        @Override public String getName() { return name; }
        @Override public MigrationContent getContent() {
            if (content == null) content = parser.parse(sql);
            return content;
        }
    }

    private Migration migration(Integer version, String name, String sql) {
        return new TestMigration(version, name, sql, migrationParser);
    }

    @PostConstruct
    void setup() throws Exception {
        migrationExecutor.ensureMigrationIndexExists().get();
    }

    @Test
    void whenCreateTableWithObjectColumn_thenMappingIsObject() throws Exception {
        String sql = """
            CREATE TABLE ct_object_test (
                id KEYWORD,
                address OBJECT (street TEXT, city KEYWORD, zip KEYWORD)
            );
            """;
        migrationExecutor.executeProjectMigrations(
            List.of(migration(1, "V1__ct_object_test", sql)), "ct_object_project").get();

        assertTrue(asyncClient.indices().exists(e -> e.index("ct_object_test")).get().value());
        GetMappingResponse mapping = client.indices().getMapping(m -> m.index("ct_object_test"));
        Map<String, Property> props = mapping.get("ct_object_test").mappings().properties();

        assertEquals(Property.Kind.Object, props.get("address")._kind());
        Map<String, Property> subProps = props.get("address").object().properties();
        assertEquals(Property.Kind.Text, subProps.get("street")._kind());
        assertEquals(Property.Kind.Keyword, subProps.get("city")._kind());
        assertEquals(Property.Kind.Keyword, subProps.get("zip")._kind());
    }

    @Test
    void whenCreateTableWithObjectNotIndexed_thenObjectDisabled() throws Exception {
        String sql = """
            CREATE TABLE ct_object_ni_test (
                id KEYWORD,
                payload OBJECT (raw TEXT, size INTEGER) NOT INDEXED
            );
            """;
        migrationExecutor.executeProjectMigrations(
            List.of(migration(1, "V1__ct_object_ni_test", sql)), "ct_object_ni_project").get();

        GetMappingResponse mapping = client.indices().getMapping(m -> m.index("ct_object_ni_test"));
        Map<String, Property> props = mapping.get("ct_object_ni_test").mappings().properties();

        assertEquals(Property.Kind.Object, props.get("payload")._kind());
        assertFalse(props.get("payload").object().enabled());
    }

    @Test
    void whenCreateTableWithNestedColumn_thenMappingIsNested() throws Exception {
        String sql = """
            CREATE TABLE ct_nested_test (
                id KEYWORD,
                tags NESTED (label TEXT, value KEYWORD)
            );
            """;
        migrationExecutor.executeProjectMigrations(
            List.of(migration(1, "V1__ct_nested_test", sql)), "ct_nested_project").get();

        assertTrue(asyncClient.indices().exists(e -> e.index("ct_nested_test")).get().value());
        GetMappingResponse mapping = client.indices().getMapping(m -> m.index("ct_nested_test"));
        Map<String, Property> props = mapping.get("ct_nested_test").mappings().properties();

        assertEquals(Property.Kind.Nested, props.get("tags")._kind());
        Map<String, Property> subProps = props.get("tags").nested().properties();
        assertEquals(Property.Kind.Text, subProps.get("label")._kind());
        assertEquals(Property.Kind.Keyword, subProps.get("value")._kind());
    }

    @Test
    void whenCreateTableWithUnionColumn_thenMergedFlatObject() throws Exception {
        String sql = """
            CREATE TABLE ct_union_test (
                id KEYWORD,
                item UNION (
                    Book  (kind KEYWORD, title TEXT, isbn KEYWORD),
                    Video (kind KEYWORD, title TEXT, duration INTEGER)
                )
            );
            """;
        migrationExecutor.executeProjectMigrations(
            List.of(migration(1, "V1__ct_union_test", sql)), "ct_union_project").get();

        assertTrue(asyncClient.indices().exists(e -> e.index("ct_union_test")).get().value());
        GetMappingResponse mapping = client.indices().getMapping(m -> m.index("ct_union_test"));
        Map<String, Property> props = mapping.get("ct_union_test").mappings().properties();

        assertEquals(Property.Kind.Object, props.get("item")._kind());
        Map<String, Property> subProps = props.get("item").object().properties();
        // All merged fields present
        assertEquals(Property.Kind.Keyword, subProps.get("kind")._kind());
        assertEquals(Property.Kind.Text, subProps.get("title")._kind());
        assertEquals(Property.Kind.Keyword, subProps.get("isbn")._kind());
        assertEquals(Property.Kind.Integer, subProps.get("duration")._kind());
    }

    @Test
    void whenUnionHasConflictingFieldTypes_thenExceptionThrown() {
        // Parse succeeds — the conflict is detected at mapping time in TypeMapper
        String sql = """
            CREATE TABLE ct_union_conflict (
                id KEYWORD,
                item UNION (
                    TypeA (name TEXT),
                    TypeB (name KEYWORD)
                )
            );
            """;
        MigrationContent content = migrationParser.parse(sql);
        var createStmt = (org.kinotic.sql.domain.statements.CreateTableStatement) content.statements().get(0);
        var unionColumn = createStmt.columns().stream()
            .filter(c -> c.name().equals("item"))
            .findFirst().orElseThrow();

        assertThrows(IllegalArgumentException.class, () -> TypeMapper.mapType(unionColumn));
    }

    @Test
    void whenAlterTableAddObjectColumn_thenMappingUpdated() throws Exception {
        String createSql = """
            CREATE TABLE ct_alter_test (
                id KEYWORD,
                name TEXT
            );
            """;
        String alterSql = """
            ALTER TABLE ct_alter_test ADD COLUMN address OBJECT (street TEXT, city KEYWORD);
            """;
        migrationExecutor.executeProjectMigrations(
            List.of(
                migration(1, "V1__ct_alter_create", createSql),
                migration(2, "V2__ct_alter_add_col", alterSql)
            ), "ct_alter_project").get();

        GetMappingResponse mapping = client.indices().getMapping(m -> m.index("ct_alter_test"));
        Map<String, Property> props = mapping.get("ct_alter_test").mappings().properties();

        assertEquals(Property.Kind.Object, props.get("address")._kind());
        Map<String, Property> subProps = props.get("address").object().properties();
        assertEquals(Property.Kind.Text, subProps.get("street")._kind());
        assertEquals(Property.Kind.Keyword, subProps.get("city")._kind());
    }

    @Test
    void whenObjectInsideNested_thenDeepMappingCorrect() throws Exception {
        String sql = """
            CREATE TABLE ct_deep_test (
                id KEYWORD,
                items NESTED (
                    name TEXT,
                    meta OBJECT (source KEYWORD, score FLOAT)
                )
            );
            """;
        migrationExecutor.executeProjectMigrations(
            List.of(migration(1, "V1__ct_deep_test", sql)), "ct_deep_project").get();

        GetMappingResponse mapping = client.indices().getMapping(m -> m.index("ct_deep_test"));
        Map<String, Property> props = mapping.get("ct_deep_test").mappings().properties();

        assertEquals(Property.Kind.Nested, props.get("items")._kind());
        Map<String, Property> nestedProps = props.get("items").nested().properties();
        assertEquals(Property.Kind.Text, nestedProps.get("name")._kind());
        assertEquals(Property.Kind.Object, nestedProps.get("meta")._kind());
        Map<String, Property> metaProps = nestedProps.get("meta").object().properties();
        assertEquals(Property.Kind.Keyword, metaProps.get("source")._kind());
        assertEquals(Property.Kind.Float, metaProps.get("score")._kind());
    }

    @Test
    void whenCreateTableWithUnionNotIndexed_thenObjectDisabled() throws Exception {
        String sql = """
            CREATE TABLE ct_union_ni_test (
                id KEYWORD,
                item UNION (TypeA (kind KEYWORD, x TEXT)) NOT INDEXED
            );
            """;
        migrationExecutor.executeProjectMigrations(
            List.of(migration(1, "V1__ct_union_ni_test", sql)), "ct_union_ni_project").get();

        GetMappingResponse mapping = client.indices().getMapping(m -> m.index("ct_union_ni_test"));
        Map<String, Property> props = mapping.get("ct_union_ni_test").mappings().properties();

        assertEquals(Property.Kind.Object, props.get("item")._kind());
        assertFalse(props.get("item").object().enabled());
    }

    @Test
    void whenCreateTableWithObjectInsideObject_thenDeepMappingCorrect() throws Exception {
        String sql = """
            CREATE TABLE ct_obj_obj_test (
                id KEYWORD,
                location OBJECT (
                    label TEXT,
                    coords OBJECT (lat DOUBLE, lon DOUBLE)
                )
            );
            """;
        migrationExecutor.executeProjectMigrations(
            List.of(migration(1, "V1__ct_obj_obj_test", sql)), "ct_obj_obj_project").get();

        GetMappingResponse mapping = client.indices().getMapping(m -> m.index("ct_obj_obj_test"));
        Map<String, Property> props = mapping.get("ct_obj_obj_test").mappings().properties();

        assertEquals(Property.Kind.Object, props.get("location")._kind());
        Property coords = props.get("location").object().properties().get("coords");
        assertEquals(Property.Kind.Object, coords._kind());
        assertEquals(Property.Kind.Double, coords.object().properties().get("lat")._kind());
        assertEquals(Property.Kind.Double, coords.object().properties().get("lon")._kind());
    }

    @Test
    void whenCreateTableWithUnionInsideObject_thenMappingCorrect() throws Exception {
        String sql = """
            CREATE TABLE ct_union_obj_test (
                id KEYWORD,
                wrapper OBJECT (
                    ref KEYWORD,
                    item UNION (
                        TypeA (kind KEYWORD, x TEXT),
                        TypeB (kind KEYWORD, y INTEGER)
                    )
                )
            );
            """;
        migrationExecutor.executeProjectMigrations(
            List.of(migration(1, "V1__ct_union_obj_test", sql)), "ct_union_obj_project").get();

        GetMappingResponse mapping = client.indices().getMapping(m -> m.index("ct_union_obj_test"));
        Map<String, Property> props = mapping.get("ct_union_obj_test").mappings().properties();

        assertEquals(Property.Kind.Object, props.get("wrapper")._kind());
        Property item = props.get("wrapper").object().properties().get("item");
        assertEquals(Property.Kind.Object, item._kind());
        assertTrue(item.object().properties().containsKey("x"));
        assertTrue(item.object().properties().containsKey("y"));
    }

    @Test
    void whenAlterTableAddNestedColumn_thenMappingUpdated() throws Exception {
        String createSql = """
            CREATE TABLE ct_alter_nested_test (
                id KEYWORD,
                name TEXT
            );
            """;
        String alterSql = """
            ALTER TABLE ct_alter_nested_test ADD COLUMN tags NESTED (label TEXT, value KEYWORD);
            """;
        migrationExecutor.executeProjectMigrations(
            List.of(
                migration(1, "V1__ct_alter_nested_create", createSql),
                migration(2, "V2__ct_alter_nested_add",    alterSql)
            ), "ct_alter_nested_project").get();

        GetMappingResponse mapping = client.indices().getMapping(m -> m.index("ct_alter_nested_test"));
        Map<String, Property> props = mapping.get("ct_alter_nested_test").mappings().properties();

        assertEquals(Property.Kind.Nested, props.get("tags")._kind());
        Map<String, Property> subProps = props.get("tags").nested().properties();
        assertEquals(Property.Kind.Text,    subProps.get("label")._kind());
        assertEquals(Property.Kind.Keyword, subProps.get("value")._kind());
    }

    @Test
    void whenAlterTableAddUnionColumn_thenMappingUpdated() throws Exception {
        String createSql = """
            CREATE TABLE ct_alter_union_test (
                id KEYWORD,
                name TEXT
            );
            """;
        String alterSql = """
            ALTER TABLE ct_alter_union_test ADD COLUMN item UNION (
                TypeA (kind KEYWORD, x TEXT),
                TypeB (kind KEYWORD, y INTEGER)
            );
            """;
        migrationExecutor.executeProjectMigrations(
            List.of(
                migration(1, "V1__ct_alter_union_create", createSql),
                migration(2, "V2__ct_alter_union_add",    alterSql)
            ), "ct_alter_union_project").get();

        GetMappingResponse mapping = client.indices().getMapping(m -> m.index("ct_alter_union_test"));
        Map<String, Property> props = mapping.get("ct_alter_union_test").mappings().properties();

        assertEquals(Property.Kind.Object, props.get("item")._kind());
        Map<String, Property> subProps = props.get("item").object().properties();
        assertTrue(subProps.containsKey("kind"));
        assertTrue(subProps.containsKey("x"));
        assertTrue(subProps.containsKey("y"));
    }

    @Test
    void whenInsertObjectAndNestedLiterals_thenSubDocumentsStored() throws Exception {
        String createSql = """
            CREATE TABLE ct_insert_test (
                id      KEYWORD,
                address OBJECT (street TEXT, city KEYWORD, coords OBJECT (lat DOUBLE, lon DOUBLE)),
                tags    NESTED (label TEXT, value KEYWORD)
            );
            """;
        String insertSql = """
            INSERT INTO ct_insert_test (id, address, tags) VALUES (
                'p-1',
                { street: '1 Main St', city: 'Springfield', coords: { lat: 30.26, lon: -97.74 } },
                [ { label: 'Release', value: 'v1' }, { label: 'Area', value: 'sql' } ]
            ) WITH REFRESH;
            """;
        migrationExecutor.executeProjectMigrations(
            List.of(migration(1, "V1__ct_insert_create", createSql),
                    migration(2, "V2__ct_insert_data",   insertSql)), "ct_insert_project").get();

        @SuppressWarnings("rawtypes")
        GetResponse<Map> response = client.get(g -> g.index("ct_insert_test").id("p-1"), Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> source = response.source();

        @SuppressWarnings("unchecked")
        Map<String, Object> address = (Map<String, Object>) source.get("address");
        assertEquals("1 Main St", address.get("street"));
        assertEquals("Springfield", address.get("city"));

        @SuppressWarnings("unchecked")
        Map<String, Object> coords = (Map<String, Object>) address.get("coords");
        assertEquals(30.26, ((Number) coords.get("lat")).doubleValue());
        assertEquals(-97.74, ((Number) coords.get("lon")).doubleValue());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tags = (List<Map<String, Object>>) source.get("tags");
        assertEquals(2, tags.size());
        assertEquals("Release", tags.get(0).get("label"));
        assertEquals("sql", tags.get(1).get("value"));
    }

    @Test
    void whenInsertNestedLiteral_thenElementsIndependentlyQueryable() throws Exception {
        String createSql = """
            CREATE TABLE ct_insert_nested_test (
                id   KEYWORD,
                tags NESTED (label KEYWORD, value KEYWORD)
            );
            """;
        String insertSql = """
            INSERT INTO ct_insert_nested_test (id, tags) VALUES (
                'a-1',
                [ { label: 'Release', value: 'v1' }, { label: 'Area', value: 'sql' } ]
            ) WITH REFRESH;
            """;
        migrationExecutor.executeProjectMigrations(
            List.of(migration(1, "V1__ct_insert_nested_create", createSql),
                    migration(2, "V2__ct_insert_nested_data",   insertSql)), "ct_insert_nested_project").get();

        // A nested query matches only when both terms come from the same array element, so a hit on
        // ('Release','v1') and none on ('Release','sql') means each element became its own sub-document
        assertEquals(1, nestedTagMatches("Release", "v1"));
        assertEquals(0, nestedTagMatches("Release", "sql"));
    }

    @SuppressWarnings("rawtypes")
    private long nestedTagMatches(String label, String value) throws Exception {
        SearchResponse<Map> response = client.search(s -> s
            .index("ct_insert_nested_test")
            .query(q -> q.nested(n -> n
                .path("tags")
                .query(nq -> nq.bool(b -> b
                    .filter(f -> f.term(t -> t.field("tags.label").value(label)))
                    .filter(f -> f.term(t -> t.field("tags.value").value(value))))))),
            Map.class);
        return response.hits().total().value();
    }

    @Test
    void whenUpdateSetsObjectLiteral_thenMergedIntoStoredObject() throws Exception {
        String createSql = """
            CREATE TABLE ct_update_test (
                id      KEYWORD,
                address OBJECT (street TEXT, city KEYWORD, coords OBJECT (lat DOUBLE, lon DOUBLE)),
                tags    NESTED (label TEXT, value KEYWORD)
            );
            """;
        String insertSql = """
            INSERT INTO ct_update_test (id, address, tags) VALUES (
                'p-1',
                { street: '1 Main St', city: 'Springfield', coords: { lat: 1.0, lon: -97.74 } },
                [ { label: 'Area', value: 'sql' } ]
            ) WITH REFRESH;
            """;
        String updateSql = """
            UPDATE ct_update_test
               SET address = { city: 'Shelbyville', coords: { lat: 30.26 } },
                   tags    = [ { label: 'Area', value: 'grammar' }, { label: 'Release', value: 'v2' } ]
             WHERE id == 'p-1' WITH REFRESH;
            """;
        migrationExecutor.executeProjectMigrations(
            List.of(migration(1, "V1__ct_update_create", createSql),
                    migration(2, "V2__ct_update_data",   insertSql),
                    migration(3, "V3__ct_update_apply",  updateSql)), "ct_update_project").get();

        @SuppressWarnings("rawtypes")
        GetResponse<Map> response = client.get(g -> g.index("ct_update_test").id("p-1"), Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> source = response.source();

        @SuppressWarnings("unchecked")
        Map<String, Object> address = (Map<String, Object>) source.get("address");
        assertEquals("1 Main St", address.get("street"), "sub-field the statement left out is kept");
        assertEquals("Shelbyville", address.get("city"));

        // The merge is recursive: coords keeps the lon the statement never mentioned
        @SuppressWarnings("unchecked")
        Map<String, Object> coords = (Map<String, Object>) address.get("coords");
        assertEquals(30.26, ((Number) coords.get("lat")).doubleValue());
        assertEquals(-97.74, ((Number) coords.get("lon")).doubleValue());

        // An array has no sub-fields to merge, so it is replaced outright
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tags = (List<Map<String, Object>>) source.get("tags");
        assertEquals(2, tags.size());
        assertEquals("grammar", tags.get(0).get("value"));
        assertEquals("Release", tags.get(1).get("label"));
    }

    @Test
    void whenValueIsNull_thenFieldAbsentFromDocument() throws Exception {
        String createSql = """
            CREATE TABLE ct_null_test (
                id       KEYWORD,
                nickname KEYWORD,
                address  OBJECT (street TEXT, city KEYWORD)
            );
            """;
        String insertSql = """
            INSERT INTO ct_null_test (id, nickname, address) VALUES (
                'p-1', 'Jay', { street: '1 Main St', city: 'Springfield' }
            ) WITH REFRESH;
            INSERT INTO ct_null_test (id, nickname, address) VALUES (
                'p-2', null, { street: '2 Elm St', city: 'Shelbyville' }
            ) WITH REFRESH;
            """;
        String updateSql = """
            UPDATE ct_null_test
               SET nickname = null,
                   address  = { street: null }
             WHERE id == 'p-1' WITH REFRESH;
            """;
        migrationExecutor.executeProjectMigrations(
            List.of(migration(1, "V1__ct_null_create", createSql),
                    migration(2, "V2__ct_null_data",   insertSql),
                    migration(3, "V3__ct_null_clear",  updateSql)), "ct_null_project").get();

        @SuppressWarnings("rawtypes")
        GetResponse<Map> cleared = client.get(g -> g.index("ct_null_test").id("p-1"), Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> clearedSource = cleared.source();
        assertFalse(clearedSource.containsKey("nickname"), "a column set to null is removed");

        @SuppressWarnings("unchecked")
        Map<String, Object> address = (Map<String, Object>) clearedSource.get("address");
        assertFalse(address.containsKey("street"), "a sub-field set to null is removed");
        assertEquals("Springfield", address.get("city"), "the sub-field the statement left out is kept");

        @SuppressWarnings("rawtypes")
        GetResponse<Map> inserted = client.get(g -> g.index("ct_null_test").id("p-2"), Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> insertedSource = inserted.source();
        assertFalse(insertedSource.containsKey("nickname"), "a null INSERT value stores no field at all");
    }

    @Test
    void whenInsertObjectLiteralWithUndeclaredSubField_thenMigrationFails() throws Exception {
        String createSql = """
            CREATE TABLE ct_insert_strict_test (
                id      KEYWORD,
                address OBJECT (street TEXT, city KEYWORD)
            );
            """;
        String insertSql = """
            INSERT INTO ct_insert_strict_test (id, address)
                VALUES ('p-1', { street: '1 Main St', country: 'US' }) WITH REFRESH;
            """;
        migrationExecutor.executeProjectMigrations(
            List.of(migration(1, "V1__ct_insert_strict_create", createSql)), "ct_insert_strict_project").get();

        // dynamic:strict means the mapping, not the parser, is what rejects a mistyped sub-field
        assertThrows(ExecutionException.class, () -> migrationExecutor.executeProjectMigrations(
            List.of(migration(2, "V2__ct_insert_strict_data", insertSql)), "ct_insert_strict_project").get());
    }

    @Test
    void whenInsertDocumentWithUndeclaredSubField_thenRejected() throws Exception {
        String sql = """
            CREATE TABLE ct_strict_test (
                id KEYWORD,
                address OBJECT (street TEXT, city KEYWORD)
            );
            """;
        migrationExecutor.executeProjectMigrations(
            List.of(migration(1, "V1__ct_strict_test", sql)), "ct_strict_project").get();

        // dynamic:strict means undeclared sub-fields are rejected at write time
        Map<String, Object> doc = new HashMap<>();
        doc.put("id", "test-1");
        Map<String, Object> address = new HashMap<>();
        address.put("street", "123 Main St");
        address.put("city", "Springfield");
        address.put("country", "US"); // undeclared
        doc.put("address", address);

        assertThrows(ElasticsearchException.class, () ->
            client.index(i -> i.index("ct_strict_test").id("test-1").document(doc))
        );
    }
}
