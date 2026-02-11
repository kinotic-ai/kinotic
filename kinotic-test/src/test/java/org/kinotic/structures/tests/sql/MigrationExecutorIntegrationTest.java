package org.kinotic.structures.tests.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.kinotic.structures.support.elastic.ElasticTestBase;
import org.kinotic.structures.sql.domain.Migration;
import org.kinotic.structures.sql.domain.MigrationContent;
import org.kinotic.structures.sql.executor.MigrationExecutor;
import org.kinotic.structures.sql.parsers.MigrationParser;
import org.springframework.beans.factory.annotation.Autowired;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch.cluster.GetComponentTemplateResponse;
import co.elastic.clients.elasticsearch.core.CountResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.indices.GetIndexTemplateResponse;
import co.elastic.clients.elasticsearch.indices.GetMappingResponse;

class MigrationExecutorIntegrationTest extends ElasticTestBase {

    @Autowired
    private ElasticsearchAsyncClient asyncClient;

    @Autowired
    private ElasticsearchClient client;

    @Autowired
    private MigrationExecutor migrationExecutor;

    @Autowired
    private MigrationParser migrationParser;

    // Simple in-memory Migration implementation for tests
    class TestMigration implements Migration {
        private final Integer version;
        private final String name;
        private final String sql;
        private final MigrationParser parser;
        private MigrationContent content;
        public TestMigration(Integer version, String name, String sql, MigrationParser parser) {
            this.version = version;
            this.name = name;
            this.sql = sql;
            this.parser = parser;
        }
        @Override public Integer getVersion() { return version; }
        @Override public String getName() { return name; }
        @Override public MigrationContent getContent() {
            if (content == null) {
                content = parser.parse(sql);
            }
            return content;
        }
    }

    private Migration migration(Integer version, String name, String sql) {
        return new TestMigration(version, name, sql, migrationParser);
    }

    @Test
    void whenNoMigrations_thenNoMigrationsApplied() throws Exception {
        // Given
        List<Migration> migrations = List.of();

        // When
        migrationExecutor.executeProjectMigrations(migrations, "test_project_no_migrations").get();

        // Then
        // No exception should be thrown
    }

    @Test
    void whenCreateTableMigration_thenTableCreated() throws Exception {
        // Given
        String migrationContent = """
            CREATE TABLE test_table_create (
                id TEXT,
                name TEXT,
                age INTEGER,
                active BOOLEAN,
                created_at DATE
            );
            """;

        Migration migration = migration(1, "V1__create_test_table", migrationContent);

        // When
        migrationExecutor.executeProjectMigrations(List.of(migration), "test_project_create_table").get();

        // Then
        assertTrue(asyncClient.indices().exists(e -> e.index("test_table_create")).get().value());
        
        // Verify mapping
        GetMappingResponse mapping = client.indices().getMapping(m -> m.index("test_table_create"));
        var indexMapping = mapping.get("test_table_create");
        assertNotNull(indexMapping, "Mapping for test_table_create should exist");
        Map<String, Property> properties = indexMapping.mappings().properties();
        
        assertNotNull(properties.get("id"), "Property 'id' should exist");
        assertEquals("Text", properties.get("id")._kind().name());
        assertEquals("Text", properties.get("name")._kind().name());
        assertEquals("Integer", properties.get("age")._kind().name());
        assertEquals("Boolean", properties.get("active")._kind().name());
        assertEquals("Date", properties.get("created_at")._kind().name());
    }

    // @Test
    void whenCreateIndexTemplate_thenTemplateCreated() throws Exception {
        // Given
        String componentTemplateContent = """
            CREATE COMPONENT TEMPLATE test_template_index (
                NUMBER_OF_SHARDS == 3,
                NUMBER_OF_REPLICAS == 1,
                id KEYWORD,
                name TEXT
            );
            """;
        String indexTemplateContent = """
            CREATE INDEX TEMPLATE test_index_template_index FOR "test-index-*" USING test_template_index WITH (
                NUMBER_OF_SHARDS == 2,
                NUMBER_OF_REPLICAS == 1,
                status TEXT
            );
            """;


        Migration componentTemplateMigration = migration(1, "V1__create_component_template", componentTemplateContent);
        Migration indexTemplateMigration = migration(2, "V2__create_index_template", indexTemplateContent);

        // When
        migrationExecutor.executeProjectMigrations(List.of(componentTemplateMigration, indexTemplateMigration), "test_project_index_template").get();

        // Then
        GetComponentTemplateResponse componentTemplate = client.cluster().getComponentTemplate(g -> g.name("test_template_index"));
        assertTrue(componentTemplate.componentTemplates().stream().anyMatch(t -> t.name().equals("test_template_index")));

        GetIndexTemplateResponse indexTemplate = client.indices().getIndexTemplate(g -> g.name("test_index_template_index"));
        assertTrue(indexTemplate.indexTemplates().stream().anyMatch(t -> t.name().equals("test_index_template_index")));
    }

    @Test
    void whenAlterTable_thenColumnAdded() throws Exception {
        // Given
        String createTableContent = """
            CREATE TABLE test_table_alter (
                id TEXT,
                name TEXT
            );
            """;
        String alterTableContent = """
            ALTER TABLE test_table_alter ADD COLUMN age INTEGER;
            """;

        Migration createTableMigration = migration(1, "V1__create_test_table", createTableContent);
        Migration alterTableMigration = migration(2, "V2__add_age_column", alterTableContent);

        // When
        migrationExecutor.executeProjectMigrations(List.of(createTableMigration, alterTableMigration), "test_project_alter_table").get();

        // Then
        GetMappingResponse mapping = client.indices().getMapping(m -> m.index("test_table_alter"));
        var indexMapping = mapping.get("test_table_alter");
        assertNotNull(indexMapping, "Mapping for test_table_alter should exist");
        Map<String, Property> properties = indexMapping.mappings().properties();
        assertNotNull(properties.get("age"), "Property 'age' should exist");
        assertEquals("Integer", properties.get("age")._kind().name());
    }

    @Test
    void whenReindex_thenDataReindexed() throws Exception {
        // Given
        String createSourceContent = """
            CREATE TABLE test_table_reindex_source (
                id TEXT,
                name TEXT
            );
            """;
        String createDestContent = """
            CREATE TABLE test_table_reindex_dest (
                id TEXT,
                name TEXT
            );
            """;
        String insertContent = """
            INSERT INTO test_table_reindex_source (id, name) VALUES ('1', 'test') WITH REFRESH;
            """;
        String reindexContent = """
            REINDEX test_table_reindex_source INTO test_table_reindex_dest WITH (
                CONFLICTS == PROCEED,
                MAX_DOCS == 100,
                SLICES == AUTO,
                SIZE == 1000,
                SOURCE_FIELDS == 'id,name',
                QUERY == 'name:test',
                WAIT == TRUE
            );
            """;

        Migration createSourceMigration = migration(1, "V1__create_source_table", createSourceContent);

        Migration createDestMigration = migration(2, "V2__create_dest_table", createDestContent);

        Migration insertMigration = migration(3, "V3__insert_test_data", insertContent);

        Migration reindexMigration = migration(4, "V4__reindex_data", reindexContent);

        // When
        migrationExecutor.executeProjectMigrations(List.of(createSourceMigration, createDestMigration, insertMigration, reindexMigration), "test_project_reindex").get();

        // Then
        CountResponse count = client.count(c -> c.index("test_table_reindex_dest"));
        assertEquals(1, count.count());
    }

    @SuppressWarnings({ "unchecked", "null" })
    @Test
    void whenReindexWithScript_thenDataTransformed() throws Exception {
        // Given
        String createSourceContent = """
            CREATE TABLE test_table_reindex_script_source (
                id TEXT,
                name TEXT,
                age INTEGER
            );
            """;
        String createDestContent = """
            CREATE TABLE test_table_reindex_script_dest (
                id TEXT,
                name TEXT,
                age INTEGER,
                status TEXT
            );
            """;
        String insertContent = """
            INSERT INTO test_table_reindex_script_source (id, name, age) VALUES ('1', 'john', 25) WITH REFRESH;
            """;
        String reindexContent = """
            REINDEX test_table_reindex_script_source INTO test_table_reindex_script_dest WITH (
                CONFLICTS == PROCEED,
                SCRIPT == 'ctx._source.status = ctx._source.age >= 18 ? "adult" : "minor"',
                WAIT == TRUE
            );
            """;

        Migration createSourceMigration = migration(1, "V1__create_source_table", createSourceContent);
        Migration createDestMigration = migration(2, "V2__create_dest_table", createDestContent);
        Migration insertMigration = migration(3, "V3__insert_test_data", insertContent);
        Migration reindexMigration = migration(4, "V4__reindex_with_script", reindexContent);

        // When
        migrationExecutor.executeProjectMigrations(List.of(createSourceMigration, createDestMigration, insertMigration, reindexMigration), "test_project_reindex_script").get();

        // Then
        CountResponse count = client.count(c -> c.index("test_table_reindex_script_dest"));
        assertEquals(1, count.count());
        
        // Verify the script transformation was applied
        @SuppressWarnings("rawtypes")
        SearchResponse<Map> response = client.search(s -> s
            .index("test_table_reindex_script_dest")
            .query(q -> q.term(t -> t.field("id").value("1"))),
            Map.class
        );
        
        Map<String, Object> document = response.hits().hits().get(0).source();
        assertEquals("john", document.get("name"));
        assertEquals(25, document.get("age"));
        assertEquals("adult", document.get("status")); // This field was added by the script
    }

    @SuppressWarnings("null")
    @Test
    void whenUpdate_thenDataUpdated() throws Exception {
        // Given
        String createTableContent = """
            CREATE TABLE test_table_update (
                id TEXT,
                name TEXT,
                age INTEGER
            );
            """;
        String insertContent = """
            INSERT INTO test_table_update (id, name, age) VALUES ('1', 'test', 20) WITH REFRESH;
            """;
        String updateContent = """
            UPDATE test_table_update SET age == 21 WHERE id == '1' WITH REFRESH;
            """;

        Migration createTableMigration = migration(1, "V1__create_test_table", createTableContent);

        Migration insertMigration = migration(2, "V2__insert_test_data", insertContent);

        Migration updateMigration = migration(3, "V3__update_age", updateContent);

        // When
        migrationExecutor.executeProjectMigrations(List.of(createTableMigration, insertMigration, updateMigration), "test_project_update").get();

        // Then
        @SuppressWarnings("rawtypes")
        SearchResponse<Map> response = client.search(s -> s
            .index("test_table_update")
            .query(q -> q.term(t -> t.field("id").value("1"))),
            Map.class
        );
        assertEquals(21, response.hits().hits().get(0).source().get("age"));
    }

    @Test
    void whenDelete_thenDataDeleted() throws Exception {
        // Given
        String createTableContent = """
            CREATE TABLE test_table_delete (
                id TEXT,
                name TEXT
            );
            """;
        String insertContent = """
            INSERT INTO test_table_delete (id, name) VALUES ('1', 'test') WITH REFRESH;
            """;
        String deleteContent = """
            DELETE FROM test_table_delete WHERE id == '1' WITH REFRESH;
            """;

        Migration createTableMigration = migration(1, "V1__create_test_table", createTableContent);

        Migration insertMigration = migration(2, "V2__insert_test_data", insertContent);

        Migration deleteMigration = migration(3, "V3__delete_data", deleteContent);

        // When
        migrationExecutor.executeProjectMigrations(List.of(createTableMigration, insertMigration, deleteMigration), "test_project_delete").get();

        // Then
        CountResponse count = client.count(c -> c.index("test_table_delete"));
        assertEquals(0, count.count());
    }

    @Test
    void whenInvalidVersionNumber_thenExceptionThrown() throws Exception {

        Migration migration = migration(null, "Vinvalid__create_test_table", "");

        // When/Then
        assertThrows(IllegalArgumentException.class, () ->
            migrationExecutor.executeProjectMigrations(List.of(migration), "test_project_invalid_version").get()
        );
    }

    @Test
    void whenDuplicateVersionNumber_thenExceptionThrown() throws Exception {

        Migration migration1 = migration(1, "V1__create_test_table1", "");

        Migration migration2 = migration(1, "V1__create_test_table2", "");

        // When/Then
        assertThrows(IllegalStateException.class, () -> 
            migrationExecutor.executeProjectMigrations(List.of(migration1, migration2), "test_project_duplicate_version").get()
        );
    }

    @Test
    void whenCreateTableIfNotExists_thenNoErrorOnSecondCreate() throws Exception {
        // Given
        String createTableContent = """
            CREATE TABLE IF NOT EXISTS test_table_if_not_exists (
                id TEXT,
                name TEXT
            );
            """;

        Migration createTableMigration = migration(1, "V1__create_test_table", createTableContent);

        Migration createTableMigrationAgain = migration(2, "V2__create_test_table_again", createTableContent);

        // When
        migrationExecutor.executeProjectMigrations(List.of(createTableMigration, createTableMigrationAgain), "test_project_if_not_exists").get();

        // Then
        assertTrue(asyncClient.indices().exists(e -> e.index("test_table_if_not_exists")).get().value());
        
        // Verify mapping
        GetMappingResponse mapping = client.indices().getMapping(m -> m.index("test_table_if_not_exists"));
        var indexMapping = mapping.get("test_table_if_not_exists");
        assertNotNull(indexMapping, "Mapping for test_table_if_not_exists should exist");
        Map<String, Property> properties = indexMapping.mappings().properties();
        
        assertNotNull(properties.get("id"), "Property 'id' should exist");
        assertEquals("Text", properties.get("id")._kind().name());
        assertEquals("Text", properties.get("name")._kind().name());
    }

    @Test
    void whenCreateTableWithNumericTypes_thenTableCreated() throws Exception {
        // Given
        String migrationContent = """
            CREATE TABLE test_table_numeric (
                id TEXT,
                int_field INTEGER,
                long_field LONG,
                float_field FLOAT,
                double_field DOUBLE
            );
            """;

        Migration migration = migration(1, "V1__create_numeric_table", migrationContent);

        // When
        migrationExecutor.executeProjectMigrations(List.of(migration), "test_project_numeric_types").get();

        // Then
        assertTrue(asyncClient.indices().exists(e -> e.index("test_table_numeric")).get().value());
        
        // Verify mapping
        GetMappingResponse mapping = client.indices().getMapping(m -> m.index("test_table_numeric"));
        var indexMapping = mapping.get("test_table_numeric");
        assertNotNull(indexMapping, "Mapping for test_table_numeric should exist");
        Map<String, Property> properties = indexMapping.mappings().properties();
        
        assertNotNull(properties.get("id"), "Property 'id' should exist");
        assertEquals("Text", properties.get("id")._kind().name());
        assertEquals("Integer", properties.get("int_field")._kind().name());
        assertEquals("Long", properties.get("long_field")._kind().name());
        assertEquals("Float", properties.get("float_field")._kind().name());
        assertEquals("Double", properties.get("double_field")._kind().name());
    }
} 