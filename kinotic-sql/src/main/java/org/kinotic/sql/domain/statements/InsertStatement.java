package org.kinotic.sql.domain.statements;

import org.kinotic.sql.domain.Statement;

import java.util.List;

/**
 * Represents an INSERT statement in the DSL.
 * Inserts new documents into an Elasticsearch index with specified field values.
 * Created by Navíd Mitchell 🤝 Grok on 3/31/25.
 *
 * @param routing the Elasticsearch routing value the document is indexed under, or {@code null}
 *                to route by document id
 * @param documentId the Elasticsearch {@code _id} to index the document under, or {@code null}
 *                   to use the value of the {@code id} column
 */
public record InsertStatement(String tableName,
                            List<String> columns,
                            List<Object> values,
                            boolean refresh,
                            String routing,
                            String documentId) implements Statement {
} 