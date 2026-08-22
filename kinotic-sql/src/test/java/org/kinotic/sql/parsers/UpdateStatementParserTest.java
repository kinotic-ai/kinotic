package org.kinotic.sql.parsers;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.kinotic.sql.domain.BinaryExpression;
import org.kinotic.sql.domain.Expression;
import org.kinotic.sql.domain.LiteralExpression;
import org.kinotic.sql.domain.MigrationContent;
import org.kinotic.sql.domain.NamedParameter;
import org.kinotic.sql.domain.WhereClause;
import org.kinotic.sql.domain.statements.UpdateStatement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that SET assignments parse into the expressions the update script is built from,
 * including the object and array literals that replace a composite column's value.
 */
class UpdateStatementParserTest {

    private final MigrationParser parser = new MigrationParser(List.of(new UpdateStatementParser()));

    private UpdateStatement parseUpdate(String sql) {
        MigrationContent content = parser.parse(sql);
        assertEquals(1, content.statements().size());
        return assertInstanceOf(UpdateStatement.class, content.statements().getFirst());
    }

    private Object literalValue(UpdateStatement statement, String field) {
        Expression expression = statement.assignments().get(field);
        return assertInstanceOf(LiteralExpression.class, expression).value();
    }

    @Test
    void whenScalarAssignments_thenLiteralsCarryTypedValues() {
        UpdateStatement statement = parseUpdate(
            "UPDATE products SET name = 'Widget', quantity = 12, price = 9.99, inStock = false "
            + "WHERE sku == 'WDG-001' WITH REFRESH;");

        assertEquals("Widget", literalValue(statement, "name"));
        assertEquals(12, literalValue(statement, "quantity"));
        assertEquals(9.99, literalValue(statement, "price"));
        assertEquals(false, literalValue(statement, "inStock"));
    }

    @Test
    void whenObjectLiteralAssignment_thenLiteralCarriesMap() {
        UpdateStatement statement = parseUpdate("""
            UPDATE persons
               SET address = { street: '1 Main St', city: 'Springfield', coords: { lat: 30.26, lon: -97.74 } }
             WHERE id == 'p-1' WITH REFRESH;
            """);

        assertEquals(Map.of("street", "1 Main St",
                            "city", "Springfield",
                            "coords", Map.of("lat", 30.26, "lon", -97.74)),
                     literalValue(statement, "address"));
    }

    @Test
    void whenArrayOfObjectLiteralsAssignment_thenLiteralCarriesList() {
        UpdateStatement statement = parseUpdate("""
            UPDATE articles
               SET tags = [ { label: 'Release', value: 'v1' }, { label: 'Area', value: 'sql' } ]
             WHERE id == 'a-1';
            """);

        assertEquals(List.of(Map.of("label", "Release", "value", "v1"),
                             Map.of("label", "Area",    "value", "sql")),
                     literalValue(statement, "tags"));
    }

    @Test
    void whenEmptyArrayAssignment_thenLiteralCarriesEmptyList() {
        UpdateStatement statement = parseUpdate("UPDATE articles SET tags = [] WHERE id == 'a-1';");

        assertEquals(List.of(), literalValue(statement, "tags"));
    }

    @Test
    void whenNullAssignment_thenLiteralCarriesNull() {
        UpdateStatement statement = parseUpdate("UPDATE persons SET address = null WHERE id == 'p-1';");

        assertNull(literalValue(statement, "address"));
    }

    @Test
    void whenObjectLiteralFieldIsNull_thenMapCarriesNullForThatField() {
        UpdateStatement statement = parseUpdate(
            "UPDATE persons SET address = { city: 'Shelbyville', street: null } WHERE id == 'p-1';");

        Map<?, ?> address = (Map<?, ?>) literalValue(statement, "address");
        assertEquals("Shelbyville", address.get("city"));
        assertTrue(address.containsKey("street"), "a cleared field must stay in the literal to be applied");
        assertNull(address.get("street"));
    }

    @Test
    void whenParameterAssignment_thenNamedParameter() {
        UpdateStatement statement = parseUpdate("UPDATE products SET name = :newName WHERE sku == 'WDG-001';");

        assertEquals(new NamedParameter("newName"), statement.assignments().get("name"));
    }

    @Test
    void whenTwoParametersOnOneField_thenEachKeepsItsOwnName() {
        // The name, not the field, identifies the value: keying by field made these the same parameter
        UpdateStatement statement = parseUpdate(
            "UPDATE products SET price = :newPrice WHERE price == :oldPrice;");

        assertEquals(new NamedParameter("newPrice"), statement.assignments().get("price"));
        WhereClause.Condition condition = assertInstanceOf(WhereClause.Condition.class, statement.whereClause());
        assertEquals(":oldPrice", condition.getValue());
    }

    @Test
    void whenBinaryAssignment_thenOperandsKeptAsWritten() {
        UpdateStatement statement = parseUpdate("UPDATE products SET quantity = quantity + 1 WHERE sku == 'WDG-001';");

        BinaryExpression expression = assertInstanceOf(BinaryExpression.class, statement.assignments().get("quantity"));
        assertEquals("quantity", expression.left());
        assertEquals("+", expression.operator());
        assertEquals("1", expression.right());
    }

    @Test
    void whenMultipleAssignments_thenOrderPreserved() {
        UpdateStatement statement = parseUpdate(
            "UPDATE products SET name = 'Widget', sku = 'WDG-001', inStock = true WHERE id == '1';");

        assertEquals(List.of("name", "sku", "inStock"), List.copyOf(statement.assignments().keySet()));
    }
}
