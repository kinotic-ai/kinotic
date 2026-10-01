package org.kinotic.sql.domain.statements;

import java.util.List;

import org.kinotic.sql.domain.TableStatement;

/**
 * Represents a REINDEX statement in the DSL.
 * Reindexes data from one Elasticsearch index to another with optional configurations.
 * Created by Navíd Mitchell 🤝 Grok on 3/31/25.
 *
 * @param conflicts    "abort" or "proceed", null defaults to "abort"
 * @param maxDocs      null if not specified
 * @param slices       "auto" or integer as string, null if not specified
 * @param size         null if not specified
 * @param sourceFields null if not specified, comma-separated list
 * @param query        JSON query payload, null if not specified
 * @param script       JSON script payload, null if not specified
 * @param waitForReindex Boolean representing the WAIT option
 * @param skipIfNoSource Boolean, if true, skip reindex if source index does not exist (default false)
 */
public record ReindexStatement(String source,
                               String dest,
                               String conflicts,
                               Integer maxDocs,
                               String slices,
                               Integer size,
                               String sourceFields,
                               String query,
                               String script,
                               Boolean waitForReindex,
                               Boolean skipIfNoSource) implements TableStatement {

    /**
     * @return the source then the destination
     */
    @Override
    public List<String> tableNames() {
        return List.of(source, dest);
    }

    @Override
    public ReindexStatement withTableNames(List<String> tableNames) {
        if (tableNames.size() != 2) {
            throw new IllegalArgumentException("REINDEX addresses a source and a destination, not " + tableNames);
        }
        return new ReindexStatement(tableNames.get(0), tableNames.get(1), conflicts, maxDocs, slices, size,
                                    sourceFields, query, script, waitForReindex, skipIfNoSource);
    }
}