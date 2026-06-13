package org.kinotic.test.tests.sql;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch.indices.GetMappingResponse;
import jakarta.annotation.PostConstruct;
import org.junit.jupiter.api.Test;
import org.kinotic.sql.domain.Migration;
import org.kinotic.sql.domain.MigrationContent;
import org.kinotic.sql.executor.MigrationExecutor;
import org.kinotic.sql.executor.TypeMapper;
import org.kinotic.sql.parsers.MigrationParser;
import org.kinotic.test.support.elastic.ElasticTestBase;
import org.springframework.beans.factory.annotation.Autowired;

import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import org.kinotic.sql.domain.statements.CreateTableStatement;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CompositeTypeTest extends ElasticTestBase {

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
