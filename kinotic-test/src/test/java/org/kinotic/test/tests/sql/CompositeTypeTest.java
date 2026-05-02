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
}
