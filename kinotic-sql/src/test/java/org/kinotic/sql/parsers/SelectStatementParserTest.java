package org.kinotic.sql.parsers;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.kinotic.sql.domain.AllFields;
import org.kinotic.sql.domain.FieldReference;
import org.kinotic.sql.domain.FunctionCall;
import org.kinotic.sql.domain.MigrationContent;
import org.kinotic.sql.domain.OrderBy;
import org.kinotic.sql.domain.Projection;
import org.kinotic.sql.domain.SortDirection;
import org.kinotic.sql.domain.ValueArgument;
import org.kinotic.sql.domain.WhereClause;
import org.kinotic.sql.domain.statements.AggregateStatement;
import org.kinotic.sql.domain.statements.SelectStatement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that a SELECT parses into the projection, condition, ordering and limit a search is built from, and that
 * a SELECT that calls a function or groups parses into an aggregate.
 */
class SelectStatementParserTest {

    private final MigrationParser parser = new MigrationParser(List.of(new SelectStatementParser()));

    private SelectStatement parseSelect(String sql) {
        MigrationContent content = parser.parse(sql);
        assertEquals(1, content.statements().size());
        return assertInstanceOf(SelectStatement.class, content.statements().getFirst());
    }

    @Test
    void whenSelectStar_thenWholeDocumentWithNoCondition() {
        SelectStatement statement = parseSelect("SELECT * FROM Person;");

        assertEquals("Person", statement.tableName());
        assertTrue(statement.columns().isEmpty());
        assertNull(statement.whereClause());
        assertTrue(statement.orderBy().isEmpty());
        assertNull(statement.limit());
    }

    @Test
    void whenEveryClauseGiven_thenEachLandsOnItsOwnField() {
        SelectStatement statement = parseSelect("""
            SELECT firstName, address FROM Person
             WHERE lastName = :lastName AND age >= 18
             ORDER BY lastName, firstName DESC
             LIMIT 50;
            """);

        assertEquals(List.of("firstName", "address"), statement.columns());

        WhereClause.AndClause where = assertInstanceOf(WhereClause.AndClause.class, statement.whereClause());
        WhereClause.Condition byLastName = assertInstanceOf(WhereClause.Condition.class, where.getLeft());
        assertEquals("lastName", byLastName.getField());
        assertEquals("=", byLastName.getOperator());
        assertEquals(":lastName", byLastName.getValue());
        WhereClause.Condition adults = assertInstanceOf(WhereClause.Condition.class, where.getRight());
        assertEquals(">=", adults.getOperator());
        assertEquals("18", adults.getValue());

        assertEquals(List.of(new OrderBy("lastName", SortDirection.ASC), new OrderBy("firstName", SortDirection.DESC)),
                     statement.orderBy());
        assertEquals(50, statement.limit());
    }

    @Test
    void whenFunctionCalled_thenAggregateWithItsProjectionsGroupingAndOrder() {
        MigrationContent content = parser.parse("""
            SELECT COUNT(*) AS total, address.city, PERCENTILE(age, 95) FROM Person
             WHERE lastName = :lastName
             GROUP BY address.city
             ORDER BY total DESC
             LIMIT 10;
            """);
        AggregateStatement statement = assertInstanceOf(AggregateStatement.class, content.statements().getFirst());

        assertEquals("Person", statement.tableName());
        assertEquals(List.of(new Projection(new FunctionCall("COUNT", List.of(new AllFields())), "total"),
                             new Projection(new FieldReference("address.city"), null),
                             new Projection(new FunctionCall("PERCENTILE", List.of(new FieldReference("age"), new ValueArgument(95))), null)),
                     statement.projections());
        assertInstanceOf(WhereClause.Condition.class, statement.whereClause());
        assertEquals(List.of(new FieldReference("address.city")), statement.groupBy());
        assertEquals(List.of(new OrderBy("total", SortDirection.DESC)), statement.orderBy());
        assertEquals(10, statement.limit());
    }

    @Test
    void whenGroupedWithoutFunction_thenAggregate() {
        MigrationContent content = parser.parse("SELECT lastName FROM Person GROUP BY lastName;");

        assertInstanceOf(AggregateStatement.class, content.statements().getFirst());
    }

    @Test
    void whenPlainSelectNamesAliasOrSubField_thenRefused() {
        assertThrows(IllegalArgumentException.class, () -> parser.parse("SELECT firstName AS name FROM Person;"));
        assertThrows(IllegalArgumentException.class, () -> parser.parse("SELECT address.city FROM Person;"));
    }

    @Test
    void whenFromNamesAnythingButOneName_thenParseFails() {
        for (String sql : List.of("SELECT COUNT(*) FROM \"kinotic_*\";",
                                  "SELECT COUNT(*) FROM Person, Vehicle;",
                                  "SELECT COUNT(*) FROM Person* ;",
                                  "SELECT COUNT(*) FROM (SELECT * FROM Person);")) {
            assertThrows(IllegalArgumentException.class, () -> parser.parse(sql), sql);
        }
    }
}
