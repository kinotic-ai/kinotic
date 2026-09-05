package org.kinotic.sql.parsers;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.kinotic.sql.domain.MigrationContent;
import org.kinotic.sql.domain.NamedParameter;
import org.kinotic.sql.domain.statements.InsertStatement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that INSERT values parse into the Java objects an Elasticsearch document is built from,
 * including the object and array literals that populate OBJECT, NESTED and UNION columns.
 */
class InsertStatementParserTest {

    private final MigrationParser parser = new MigrationParser(List.of(new InsertStatementParser()));

    private InsertStatement parseInsert(String sql) {
        MigrationContent content = parser.parse(sql);
        assertEquals(1, content.statements().size());
        return assertInstanceOf(InsertStatement.class, content.statements().getFirst());
    }

    @Test
    void whenScalarValues_thenParsedByType() {
        InsertStatement statement = parseInsert(
            "INSERT INTO products (name, quantity, inStock) VALUES ('Widget', 12, true) WITH REFRESH;");

        assertEquals("products", statement.tableName());
        assertEquals(List.of("name", "quantity", "inStock"), statement.columns());
        assertEquals(List.of("Widget", 12, true), statement.values());
        assertTrue(statement.refresh());
    }

    @Test
    void whenRoutingAndDocumentIdGiven_thenBothCarriedOnTheStatement() {
        InsertStatement statement = parseInsert(
            "INSERT INTO kinotic_application (id, organizationId) VALUES ('atlas-crm', 'kinotic-test') "
            + "WITH REFRESH, ROUTING 'kinotic-test', DOCUMENT_ID 'kinotic-test-atlas-crm';");

        assertTrue(statement.refresh());
        assertEquals("kinotic-test", statement.routing());
        assertEquals("kinotic-test-atlas-crm", statement.documentId());
        assertEquals(List.of("atlas-crm", "kinotic-test"), statement.values(),
                     "the id column keeps the entity id; only the document _id is composite");
    }

    @Test
    void whenOptionsOutOfOrder_thenEachStillLandsOnItsOwnField() {
        InsertStatement statement = parseInsert(
            "INSERT INTO products (id) VALUES ('p-1') WITH DOCUMENT_ID 'acme-p-1', ROUTING 'acme';");

        assertEquals("acme", statement.routing());
        assertEquals("acme-p-1", statement.documentId());
        assertFalse(statement.refresh());
    }

    @Test
    void whenNoRoutingOptions_thenTheyAreAbsentSoTheDocumentRoutesByItsOwnId() {
        InsertStatement statement = parseInsert("INSERT INTO products (id) VALUES ('p-1') WITH REFRESH;");

        assertTrue(statement.refresh());
        assertNull(statement.routing());
        assertNull(statement.documentId());
    }

    @Test
    void whenParameterReference_thenNamedParameterInValues() {
        InsertStatement statement = parseInsert("INSERT INTO products (name, sku) VALUES (:name, :sku);");

        assertEquals(List.of(new NamedParameter("name"), new NamedParameter("sku")), statement.values());
    }

    @Test
    void whenParameterNestedInObjectLiteral_thenNamedParameterAtThatPosition() {
        InsertStatement statement = parseInsert(
            "INSERT INTO persons (address) VALUES ({ street: :street, city: 'Springfield' });");

        assertEquals(Map.of("street", new NamedParameter("street"), "city", "Springfield"),
                     statement.values().getFirst());
    }

    @Test
    void whenNullValue_thenNullInValues() {
        InsertStatement statement = parseInsert("INSERT INTO products (name, sku) VALUES ('Widget', null);");

        assertNull(statement.values().get(1));
    }

    @Test
    void whenObjectLiteral_thenMapOfSubFields() {
        InsertStatement statement = parseInsert("""
            INSERT INTO persons (id, address)
                VALUES ('p-1', { street: '1 Main St', city: 'Springfield', zip: '11111' });
            """);

        assertEquals(Map.of("street", "1 Main St", "city", "Springfield", "zip", "11111"),
                     statement.values().get(1));
    }

    @Test
    void whenArrayOfObjectLiterals_thenListOfMaps() {
        InsertStatement statement = parseInsert("""
            INSERT INTO articles (id, tags)
                VALUES ('a-1', [ { label: 'Release', value: 'v1' },
                                 { label: 'Area',    value: 'sql' } ]);
            """);

        assertEquals(List.of(Map.of("label", "Release", "value", "v1"),
                             Map.of("label", "Area",    "value", "sql")),
                     statement.values().get(1));
    }

    @Test
    void whenObjectLiteralsNested_thenMapTreeMatchesLiteral() {
        InsertStatement statement = parseInsert("""
            INSERT INTO orders (id, shipment)
                VALUES ('o-1', { carrier: 'UPS',
                                 items: [ { sku: 'WDG-001', meta: { source: 'web', score: 0.75 } } ] });
            """);

        assertEquals(Map.of("carrier", "UPS",
                            "items", List.of(Map.of("sku", "WDG-001",
                                                    "meta", Map.of("source", "web", "score", 0.75)))),
                     statement.values().get(1));
    }

    @Test
    void whenScalarArray_thenListOfScalars() {
        InsertStatement statement = parseInsert("INSERT INTO users (id, roles) VALUES ('u-1', ['ADMIN', 'USER']);");

        assertEquals(List.of("ADMIN", "USER"), statement.values().get(1));
    }

    @Test
    void whenEmptyObjectAndArrayLiterals_thenEmptyMapAndList() {
        InsertStatement statement = parseInsert("INSERT INTO articles (meta, tags) VALUES ({}, []);");

        assertEquals(Map.of(), statement.values().get(0));
        assertEquals(List.of(), statement.values().get(1));
    }

    @Test
    void whenFieldNameQuoted_thenUsedVerbatim() {
        // Quoted keys carry field names the ID rule cannot express, such as JSON payload keys
        InsertStatement statement = parseInsert(
            "INSERT INTO events (payload) VALUES ({ 'content-type': 'application/json' });");

        assertEquals(Map.of("content-type", "application/json"), statement.values().getFirst());
    }

    @Test
    void whenNumberLiterals_thenNarrowedToJavaTypes() {
        InsertStatement statement = parseInsert("""
            INSERT INTO readings (id, location, count, offset)
                VALUES ('r-1', { lat: 30.26, lon: -97.74 }, 9999999999, -3);
            """);

        assertEquals(Map.of("lat", 30.26, "lon", -97.74), statement.values().get(1));
        assertEquals(9999999999L, statement.values().get(2));
        assertEquals(-3, statement.values().get(3));
    }

    @Test
    void whenObjectLiteralRepeatsField_thenParseFails() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> parseInsert(
            "INSERT INTO persons (address) VALUES ({ city: 'Springfield', city: 'Shelbyville' });"));

        assertTrue(e.getMessage().contains("Duplicate field 'city'"), "expected duplicate field in: " + e.getMessage());
    }

    @Test
    void whenObjectLiteralUnterminated_thenParseFailsNamingLine() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> parser.parse(
            "INSERT INTO persons (address) VALUES ({ city: 'Springfield' );"));

        assertTrue(e.getMessage().contains("line 1"), "expected line number in: " + e.getMessage());
    }
}
