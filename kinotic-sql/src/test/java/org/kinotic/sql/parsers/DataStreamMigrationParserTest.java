package org.kinotic.sql.parsers;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.kinotic.sql.domain.Column;
import org.kinotic.sql.domain.ColumnType;
import org.kinotic.sql.domain.MigrationContent;
import org.kinotic.sql.domain.Statement;
import org.kinotic.sql.domain.statements.CreateDataStreamStatement;
import org.kinotic.sql.domain.statements.CreateLifecyclePolicyStatement;
import org.kinotic.sql.domain.statements.RolloverConditions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Verifies that CREATE DATA STREAM and CREATE LIFECYCLE POLICY statements parse into the
 * expected domain objects.
 */
class DataStreamMigrationParserTest {

    private final MigrationParser parser = new MigrationParser(List.of(
            new CreateDataStreamStatementParser(),
            new CreateLifecyclePolicyStatementParser()));

    private Statement parseSingle(String sql) {
        MigrationContent content = parser.parse(sql);
        assertEquals(1, content.statements().size());
        return content.statements().getFirst();
    }

    @Test
    void dataStreamWithLifecycleReference() {
        CreateDataStreamStatement statement = assertInstanceOf(CreateDataStreamStatement.class, parseSingle(
            "CREATE DATA STREAM events (level KEYWORD, message TEXT) WITH (LIFECYCLE = 'logs_retention');"));

        assertEquals("events", statement.streamName());
        assertEquals("logs_retention", statement.lifecyclePolicy());
        assertEquals(
            List.of(new Column("level", ColumnType.KEYWORD), new Column("message", ColumnType.TEXT)),
            statement.columns());
    }

    @Test
    void dataStreamWithoutLifecycleHasNullPolicy() {
        CreateDataStreamStatement statement = assertInstanceOf(CreateDataStreamStatement.class, parseSingle(
            "CREATE DATA STREAM metrics (value DOUBLE);"));

        assertEquals("metrics", statement.streamName());
        assertNull(statement.lifecyclePolicy());
        assertEquals(List.of(new Column("value", ColumnType.DOUBLE)), statement.columns());
    }

    @Test
    void lifecyclePolicyWithRolloverAndDelete() {
        CreateLifecyclePolicyStatement statement = assertInstanceOf(CreateLifecyclePolicyStatement.class, parseSingle(
            "CREATE LIFECYCLE POLICY logs_retention WITH ("
            + "ROLLOVER (MAX_AGE = '7d', MAX_SIZE = '50gb'), DELETE (MIN_AGE = '30d'));"));

        assertEquals("logs_retention", statement.policyName());
        assertEquals(new RolloverConditions("7d", "50gb", null), statement.rollover());
        assertEquals("30d", statement.deleteMinAge());
    }

    @Test
    void lifecyclePolicyRolloverByDocCount() {
        CreateLifecyclePolicyStatement statement = assertInstanceOf(CreateLifecyclePolicyStatement.class, parseSingle(
            "CREATE LIFECYCLE POLICY by_docs WITH (ROLLOVER (MAX_DOCS = 1000000));"));

        assertEquals(new RolloverConditions(null, null, 1000000), statement.rollover());
        assertNull(statement.deleteMinAge());
    }

    @Test
    void lifecyclePolicyDeleteOnly() {
        CreateLifecyclePolicyStatement statement = assertInstanceOf(CreateLifecyclePolicyStatement.class, parseSingle(
            "CREATE LIFECYCLE POLICY purge WITH (DELETE (MIN_AGE = '90d'));"));

        assertNull(statement.rollover());
        assertEquals("90d", statement.deleteMinAge());
    }
}
