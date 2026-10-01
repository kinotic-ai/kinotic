package org.kinotic.sql.parsers;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.kinotic.sql.domain.MigrationContent;
import org.kinotic.sql.domain.OrderBy;
import org.kinotic.sql.domain.SortDirection;
import org.kinotic.sql.domain.WhereClause;
import org.kinotic.sql.domain.statements.SelectStatement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that a SELECT parses into the projection, condition, ordering and limit a search is built from.
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
             WHERE lastName == :lastName AND age >= 18
             ORDER BY lastName, firstName DESC
             LIMIT 50;
            """);

        assertEquals(List.of("firstName", "address"), statement.columns());

        WhereClause.AndClause where = assertInstanceOf(WhereClause.AndClause.class, statement.whereClause());
        WhereClause.Condition byLastName = assertInstanceOf(WhereClause.Condition.class, where.getLeft());
        assertEquals("lastName", byLastName.getField());
        assertEquals("==", byLastName.getOperator());
        assertEquals(":lastName", byLastName.getValue());
        WhereClause.Condition adults = assertInstanceOf(WhereClause.Condition.class, where.getRight());
        assertEquals(">=", adults.getOperator());
        assertEquals("18", adults.getValue());

        assertEquals(List.of(new OrderBy("lastName", SortDirection.ASC), new OrderBy("firstName", SortDirection.DESC)),
                     statement.orderBy());
        assertEquals(50, statement.limit());
    }

    @Test
    void whenAggregateFunctionUsed_thenNotAStatementOfThisGrammar() {
        // aggregates run on Elasticsearch SQL, which this grammar does not describe
        assertThrows(IllegalArgumentException.class, () -> parser.parse("SELECT COUNT(firstName) FROM Person;"));
    }
}
