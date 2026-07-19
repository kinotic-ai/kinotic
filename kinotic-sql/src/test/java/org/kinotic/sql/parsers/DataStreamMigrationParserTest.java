package org.kinotic.sql.parsers;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.kinotic.sql.domain.Column;
import org.kinotic.sql.domain.ColumnType;
import org.kinotic.sql.domain.MigrationContent;
import org.kinotic.sql.domain.Statement;
import org.kinotic.sql.domain.statements.CreateDataStreamStatement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Verifies that CREATE DATA STREAM statements parse into the expected domain objects.
 */
class DataStreamMigrationParserTest {

    private final MigrationParser parser = new MigrationParser(List.of(
            new CreateDataStreamStatementParser()));

    private Statement parseSingle(String sql) {
        MigrationContent content = parser.parse(sql);
        assertEquals(1, content.statements().size());
        return content.statements().getFirst();
    }

    @Test
    void dataStreamWithRetention() {
        CreateDataStreamStatement statement = assertInstanceOf(CreateDataStreamStatement.class, parseSingle(
            "CREATE DATA STREAM events (level KEYWORD, message TEXT) WITH (DATA_RETENTION = '30d');"));

        assertEquals("events", statement.streamName());
        assertEquals("30d", statement.dataRetention());
        assertNull(statement.timeReference());
        assertEquals(
            List.of(new Column("level", ColumnType.KEYWORD), new Column("message", ColumnType.TEXT)),
            statement.columns());
    }

    @Test
    void dataStreamWithoutOptionsHasNullValues() {
        CreateDataStreamStatement statement = assertInstanceOf(CreateDataStreamStatement.class, parseSingle(
            "CREATE DATA STREAM metrics (value DOUBLE);"));

        assertEquals("metrics", statement.streamName());
        assertNull(statement.dataRetention());
        assertNull(statement.timeReference());
        assertEquals(List.of(new Column("value", ColumnType.DOUBLE)), statement.columns());
    }

    @Test
    void dataStreamWithTimeReferenceAndRetention() {
        CreateDataStreamStatement statement = assertInstanceOf(CreateDataStreamStatement.class, parseSingle(
            "CREATE DATA STREAM sensor_readings (id KEYWORD, eventTime DATE, value DOUBLE) "
            + "WITH (DATA_RETENTION = '30d', TIME_REFERENCE = 'eventTime');"));

        assertEquals("sensor_readings", statement.streamName());
        assertEquals("30d", statement.dataRetention());
        assertEquals("eventTime", statement.timeReference());
    }
}
