package org.kinotic.sql.domain;

import java.util.List;

/**
 * Represents a migration with a list of statements to execute.
 * Used to manage Elasticsearch index migrations in a SQL-like DSL.
 * Created by Navíd Mitchell 🤝 Grok on 3/31/25.
 */
public record MigrationContent(List<Statement> statements) {
}