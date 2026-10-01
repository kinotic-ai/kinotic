package org.kinotic.sql.executor;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.kinotic.sql.domain.NamedParameter;
import org.kinotic.sql.domain.statements.AggregateStatement;
import org.kinotic.sql.parsers.MigrationParser;
import org.kinotic.sql.parsers.SelectStatementParser;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies the Elasticsearch SQL an aggregate is run as: the statement names its index, and every string, boolean and
 * parameter reaches Elasticsearch as a placeholder value.
 */
class ElasticsearchSqlWriterTest {

    private final MigrationParser parser = new MigrationParser(List.of(new SelectStatementParser()));

    private ElasticsearchSql write(String sql) {
        return ElasticsearchSqlWriter.write((AggregateStatement) parser.parse(sql).statements().getFirst());
    }

    @Test
    void whenEveryClauseGiven_thenEachIsWrittenInOrder() {
        ElasticsearchSql sql = write("""
            SELECT COUNT(*) AS total, address.city FROM person_index
             WHERE lastName = :lastName AND (age >= 18 OR vip = true)
             GROUP BY address.city
             ORDER BY total DESC
             LIMIT 10;
            """);

        assertEquals("SELECT COUNT(*) AS total, address.city FROM \"person_index\""
                             + " WHERE (lastName = ? AND (age >= ? OR vip = ?))"
                             + " GROUP BY address.city ORDER BY total DESC LIMIT 10",
                     sql.statement());
        assertEquals(List.of(new NamedParameter("lastName"), 18L, true), sql.parameters());
    }

    @Test
    void whenFunctionTakesValues_thenNumbersAreWrittenAndStringsArePlaceholders() {
        ElasticsearchSql sql = write("SELECT HISTOGRAM(age, 10) AS bucket, DATE_TRUNC('month', born) FROM people GROUP BY bucket;");

        assertEquals("SELECT HISTOGRAM(age, 10) AS bucket, DATE_TRUNC(?, born) FROM \"people\" GROUP BY bucket",
                     sql.statement());
        assertEquals(List.of("month"), sql.parameters());
    }

    @Test
    void whenStringHoldsSqlText_thenItIsOnlyAPlaceholderValue() {
        ElasticsearchSql sql = write("SELECT COUNT(id) FROM people WHERE name = 'x\" FROM \"kinotic_*';");

        assertEquals("SELECT COUNT(id) FROM \"people\" WHERE name = ?", sql.statement());
        assertEquals(List.of("x\" FROM \"kinotic_*"), sql.parameters());
    }
}
