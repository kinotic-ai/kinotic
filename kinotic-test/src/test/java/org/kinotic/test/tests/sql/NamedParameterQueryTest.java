package org.kinotic.test.tests.sql;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.GetResponse;
import jakarta.annotation.PostConstruct;
import org.junit.jupiter.api.Test;
import org.kinotic.sql.domain.Migration;
import org.kinotic.sql.domain.MigrationContent;
import org.kinotic.sql.domain.Statement;
import org.kinotic.sql.domain.statements.DeleteStatement;
import org.kinotic.sql.domain.statements.InsertStatement;
import org.kinotic.sql.domain.statements.UpdateStatement;
import org.kinotic.sql.executor.MigrationExecutor;
import org.kinotic.sql.executor.executors.DeleteStatementExecutor;
import org.kinotic.sql.executor.executors.InsertStatementExecutor;
import org.kinotic.sql.executor.executors.UpdateStatementExecutor;
import org.kinotic.sql.parsers.MigrationParser;
import org.kinotic.test.support.kinotic.KinoticTestBase;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that a statement's {@code :name} parameters resolve against the values supplied to
 * {@link org.kinotic.sql.executor.StatementExecutor#executeQuery}, which is how a named query runs.
 */
class NamedParameterQueryTest extends KinoticTestBase {

    private static final String INDEX = "np_param_test";

    @Autowired
    private ElasticsearchClient client;

    @Autowired
    private MigrationExecutor migrationExecutor;

    @Autowired
    private MigrationParser migrationParser;

    @Autowired
    private InsertStatementExecutor insertStatementExecutor;

    @Autowired
    private UpdateStatementExecutor updateStatementExecutor;

    @Autowired
    private DeleteStatementExecutor deleteStatementExecutor;

    static class TestMigration implements Migration {
        private final String sql;
        private final MigrationParser parser;
        private MigrationContent content;

        TestMigration(String sql, MigrationParser parser) {
            this.sql = sql;
            this.parser = parser;
        }

        @Override public Integer getVersion() { return 1; }
        @Override public String getName() { return "V1__np_param_create"; }
        @Override public MigrationContent getContent() {
            if (content == null) content = parser.parse(sql);
            return content;
        }
    }

    @PostConstruct
    void setup() throws Exception {
        migrationExecutor.ensureMigrationIndexExists().get();
        migrationExecutor.executeProjectMigrations(List.of(new TestMigration("""
            CREATE TABLE np_param_test (
                id      KEYWORD,
                city    KEYWORD,
                active  BOOLEAN,
                address OBJECT (street TEXT, city KEYWORD)
            );
            """, migrationParser)), "np_param_project").get();
    }

    private <T extends Statement> T parse(String sql) {
        @SuppressWarnings("unchecked")
        T ret = (T) migrationParser.parse(sql).statements().getFirst();
        return ret;
    }

    private Map<String, Object> source(String id) throws Exception {
        @SuppressWarnings("rawtypes")
        GetResponse<Map> response = client.get(g -> g.index(INDEX).id(id), Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> ret = response.source();
        return ret;
    }

    @Test
    void whenInsertBindsParameters_thenSuppliedValuesStored() throws Exception {
        InsertStatement statement = parse("""
            INSERT INTO np_param_test (id, city, active, address)
                VALUES (:id, :city, :active, :address) WITH REFRESH;
            """);

        insertStatementExecutor.executeQuery(statement, Map.of(
            "id", "p-1",
            "city", "Springfield",
            "active", true,
            "address", Map.of("street", "1 Main St", "city", "Springfield"))).get();

        Map<String, Object> source = source("p-1");
        assertEquals("Springfield", source.get("city"));
        assertEquals(true, source.get("active"));
        assertEquals(Map.of("street", "1 Main St", "city", "Springfield"), source.get("address"));
    }

    @Test
    void whenParameterNestedInObjectLiteral_thenResolvedInPlace() throws Exception {
        insertStatementExecutor.executeQuery(parse("""
            INSERT INTO np_param_test (id, address) VALUES (:id, { street: :street, city: 'Springfield' })
                WITH REFRESH;
            """), Map.of("id", "p-2", "street", "2 Elm St")).get();

        assertEquals(Map.of("street", "2 Elm St", "city", "Springfield"), source("p-2").get("address"));
    }

    @Test
    void whenTwoParametersNameTheSameField_thenEachResolvesSeparately() throws Exception {
        insertStatementExecutor.executeQuery(parse(
            "INSERT INTO np_param_test (id, city) VALUES (:id, :city) WITH REFRESH;"),
            Map.of("id", "p-3", "city", "North Haverbrook")).get();

        // Both parameters sit on the city field: only their names tell the new value from the matched one.
        // The city is unique to this test — every test here shares the np_param_test index, so a
        // by-value WHERE would otherwise match another test's document.
        UpdateStatement statement = parse(
            "UPDATE np_param_test SET city = :newCity WHERE city == :oldCity WITH REFRESH;");
        Long updated = updateStatementExecutor.executeQuery(statement, Map.of(
            "newCity", "Shelbyville",
            "oldCity", "North Haverbrook")).get();

        assertEquals(1, updated);
        assertEquals("Shelbyville", source("p-3").get("city"));
    }

    @Test
    void whenParameterSuppliesAnObject_thenMergedLikeALiteral() throws Exception {
        insertStatementExecutor.executeQuery(parse(
            "INSERT INTO np_param_test (id, address) VALUES (:id, :address) WITH REFRESH;"),
            Map.of("id", "p-4", "address", Map.of("street", "4 Oak St", "city", "Springfield"))).get();

        updateStatementExecutor.executeQuery(parse(
            "UPDATE np_param_test SET address = :address WHERE id == :id WITH REFRESH;"),
            Map.of("id", "p-4", "address", Map.of("city", "Shelbyville"))).get();

        assertEquals(Map.of("street", "4 Oak St", "city", "Shelbyville"), source("p-4").get("address"),
                     "an object supplied by parameter merges, the same as one written as a literal");
    }

    @Test
    void whenDeleteBindsParameter_thenMatchedDocumentRemoved() throws Exception {
        insertStatementExecutor.executeQuery(parse(
            "INSERT INTO np_param_test (id, city) VALUES (:id, :city) WITH REFRESH;"),
            Map.of("id", "p-5", "city", "Ogdenville")).get();

        DeleteStatement statement = parse("DELETE FROM np_param_test WHERE city == :city WITH REFRESH;");
        Long deleted = deleteStatementExecutor.executeQuery(statement, Map.of("city", "Ogdenville")).get();

        assertEquals(1, deleted);
        assertFalse(client.get(g -> g.index(INDEX).id("p-5"), Map.class).found());
    }

    @Test
    void whenParameterMissing_thenCallFailsNamingIt() {
        UpdateStatement update = parse("UPDATE np_param_test SET city = :newCity WHERE id == :id WITH REFRESH;");
        IllegalArgumentException updateError = assertThrows(IllegalArgumentException.class,
            () -> updateStatementExecutor.executeQuery(update, Map.of("id", "p-1")));
        assertTrue(updateError.getMessage().contains(":newCity"),
                   "expected the parameter name in: " + updateError.getMessage());

        // Every statement reports it the same way: as a failed call, not a failed future
        InsertStatement insert = parse("INSERT INTO np_param_test (id, city) VALUES (:id, :city) WITH REFRESH;");
        IllegalArgumentException insertError = assertThrows(IllegalArgumentException.class,
            () -> insertStatementExecutor.executeQuery(insert, Map.of("id", "p-9")));
        assertTrue(insertError.getMessage().contains(":city"),
                   "expected the parameter name in: " + insertError.getMessage());

        DeleteStatement delete = parse("DELETE FROM np_param_test WHERE city == :city WITH REFRESH;");
        IllegalArgumentException deleteError = assertThrows(IllegalArgumentException.class,
            () -> deleteStatementExecutor.executeQuery(delete, Map.of()));
        assertTrue(deleteError.getMessage().contains(":city"),
                   "expected the parameter name in: " + deleteError.getMessage());
    }
}
