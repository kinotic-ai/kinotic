package org.kinotic.sql.domain.statements;

import java.util.List;

import org.kinotic.sql.domain.Column;
import org.kinotic.sql.domain.Statement;

/**
 * Represents a CREATE DATA STREAM statement in the DSL.
 * Creates an Elasticsearch data stream backed by an index template with the supplied field mappings,
 * a managed {@code @timestamp} date field, and an optional native data stream lifecycle retention
 * that ages data out. A null {@code dataRetention} leaves the stream without a managed lifecycle.
 * Created by Navíd Mitchell 🤝 Claude on 6/18/26.
 */
public record CreateDataStreamStatement(String streamName,
                                        List<Column> columns,
                                        String dataRetention) implements Statement {
}
