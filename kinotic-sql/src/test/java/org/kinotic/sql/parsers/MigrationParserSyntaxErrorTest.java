package org.kinotic.sql.parsers;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.kinotic.sql.domain.MigrationContent;
import org.kinotic.sql.domain.statements.ReindexStatement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that a migration with invalid syntax fails the parse with an error naming
 * the line and offending token, instead of being silently repaired by ANTLR error recovery.
 * Also pins the operator: '=' assigns in SET and WITH options and compares in WHERE, and '==' is not an operator.
 */
class MigrationParserSyntaxErrorTest {

    private final MigrationParser parser = new MigrationParser(List.of(
            new CreateTableStatementParser(),
            new CreateDataStreamStatementParser(),
            new CreateComponentTemplateStatementParser(),
            new ReindexStatementParser(),
            new UpdateStatementParser()));

    @Test
    void whenNotIndexedOnTypeThatDisallowsIt_thenParseFailsNamingLineAndToken() {
        // NOT INDEXED is not valid on TEXT columns
        String sql = """
            CREATE TABLE test_table (
                id KEYWORD,
                sometext TEXT NOT INDEXED
            );
            """;

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> parser.parse(sql));
        assertTrue(e.getMessage().contains("line 3"), "expected line number in: " + e.getMessage());
        assertTrue(e.getMessage().contains("'NOT'"), "expected offending token in: " + e.getMessage());
    }

    @Test
    void whenMigrationContainsUnrecognizedCharacter_thenParseFails() {
        assertThrows(IllegalArgumentException.class, () -> parser.parse(
            "CREATE TABLE test_table (id KEYWORD, amount ~ DOUBLE);"));
    }

    @Test
    void whenEqualsUsedInAssignmentsAndComparisons_thenParses() {
        MigrationContent content = parser.parse("""
            CREATE COMPONENT TEMPLATE settings (NUMBER_OF_SHARDS = 1, NUMBER_OF_REPLICAS = 0);
            CREATE DATA STREAM events (level KEYWORD) WITH (DATA_RETENTION = '30d');
            REINDEX old_events INTO new_events WITH (SKIP_IF_NO_SOURCE = TRUE);
            UPDATE events SET level = 'INFO' WHERE level = 'DEBUG';
            """);

        assertEquals(4, content.statements().size());
    }

    @Test
    void whenDoubleEqualsUsedInComparison_thenParseFailsNamingTheOperator() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> parser.parse(
            "UPDATE events SET level = 'INFO' WHERE level == 'DEBUG';"));

        assertTrue(e.getMessage().contains("line 1"), "expected line number in: " + e.getMessage());
        // '==' lexes as two '=' tokens, so the second one is where the parse stops
        assertTrue(e.getMessage().contains("near '='"), "expected offending operator in: " + e.getMessage());
    }

    @Test
    void whenKeywordsAreLowercaseAndColumnsAreNamedLikeTypes_thenParses() {
        MigrationContent content = parser.parse("""
            create table events (date date, size integer not indexed, text text, "where" keyword);
            reindex old_events into events with (wait = true, skip_if_no_source = TRUE);
            update events set size = size + 1 where date >= '2026-01-01';
            """);

        assertEquals(3, content.statements().size());
        ReindexStatement reindex = (ReindexStatement) content.statements().get(1);
        assertTrue(reindex.waitForReindex());
        assertTrue(reindex.skipIfNoSource());
    }

    @Test
    void whenNotIndexedOnTypeThatAllowsIt_thenParses() {
        MigrationContent content = parser.parse("""
            CREATE TABLE test_table (
                id KEYWORD,
                metadata KEYWORD NOT INDEXED
            );
            """);

        assertEquals(1, content.statements().size());
    }
}
