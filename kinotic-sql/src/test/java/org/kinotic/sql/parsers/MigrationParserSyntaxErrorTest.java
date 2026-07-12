package org.kinotic.sql.parsers;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.kinotic.sql.domain.MigrationContent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that a migration with invalid syntax fails the parse with an error naming
 * the line and offending token, instead of being silently repaired by ANTLR error recovery.
 */
class MigrationParserSyntaxErrorTest {

    private final MigrationParser parser = new MigrationParser(List.of(
            new CreateTableStatementParser()));

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
