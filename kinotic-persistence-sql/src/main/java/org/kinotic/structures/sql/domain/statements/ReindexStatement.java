package org.kinotic.structures.sql.domain.statements;

import org.kinotic.structures.sql.domain.Statement;

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
                               Boolean skipIfNoSource) implements Statement {}