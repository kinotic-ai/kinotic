package org.mindignited.structures.sql.executor;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch.core.search.TotalHits;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import org.mindignited.structures.sql.domain.Migration;
import org.mindignited.structures.sql.domain.MigrationContent;
import org.mindignited.structures.sql.domain.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Executes migrations against Elasticsearch, ensuring idempotency by tracking applied versions.
 * 
 * This executor supports two types of migrations:
 * 1. System-level migrations - Applied during application startup via the SystemMigrator
 * 2. Project-specific migrations - Can be applied on-demand at runtime from external sources
 * 
 * Each project tracks its own set of applied migrations independently, allowing for
 * isolated migration per project.
 * 
 * Created by Navíd Mitchell 🤝 Grok on 3/31/25.
 */
@Component
public class MigrationExecutor {
    private static final Logger log = LoggerFactory.getLogger(MigrationExecutor.class);
    private static final String MIGRATION_INDEX = "migration_history";
    public static final String SYSTEM_PROJECT = "structures_system";

    private final ElasticsearchAsyncClient client;
    private final List<StatementExecutor<?, ?>> executors;

    public MigrationExecutor(ElasticsearchAsyncClient client, 
                             List<StatementExecutor<?, ?>> executors) {
        this.client = client;
        this.executors = executors;
    }

    /**
     * Ensures that the migration tracking index exists in Elasticsearch
     * @return CompletableFuture<Boolean> that completes with true if index was created, false if it already existed
     */
    public CompletableFuture<Boolean> ensureMigrationIndexExists() {
        return client.indices().exists(ExistsRequest.of(r -> r.index(MIGRATION_INDEX)))
            .thenCompose(exists -> {
                if (!exists.value()) {
                    log.info("Creating migration history index...");
                    return client.indices().create(c -> c
                            .index(MIGRATION_INDEX)
                            .mappings(m -> m
                                    .properties("version", p -> p.integer(i -> i))
                                    .properties("projectId", p -> p.keyword(k -> k))
                                    .properties("appliedAt", p -> p.date(d -> d))
                                    .properties("name", p -> p.keyword(k -> k))
                                    .properties("durationMs", p -> p.long_(l -> l))
                            )
                    ).thenApply(response -> true);
                }
                return CompletableFuture.completedFuture(false);
            });
    }

    /**
     * Executes system-level migrations that apply to the entire application
     */
    public CompletableFuture<Void> executeSystemMigrations(List<Migration> migrations) {
        return executeMigrationsForProject(migrations, SYSTEM_PROJECT);
    }

    /**
     * Executes project-specific migrations
     * These will be executed on request for a specific project
     */
    public CompletableFuture<Void> executeProjectMigrations(List<Migration> migrations, String projectId) {
        if (projectId == null || projectId.isEmpty()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Project ID cannot be null or empty"));
        }
        return executeMigrationsForProject(migrations, projectId);
    }

    /**
     * Executes migrations for a specific project context
     */
    private CompletableFuture<Void> executeMigrationsForProject(List<Migration> migrations, String projectId) {
        log.info("Executing migrations for project {}", projectId);
        // Always make a copy so we don't sort the original reference
        List<Migration> sortedMigrations = new ArrayList<>(migrations);
        sortedMigrations.sort(Comparator.comparingInt(Migration::getVersion));
        // Track seen versions to detect duplicates
        Set<Integer> seenVersions = new HashSet<>();
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (Migration migration : sortedMigrations) {
            Integer version = migration.getVersion();
            if (!isValidVersion(version)) {
                throw new IllegalArgumentException("Invalid migration version for " + migration.getName() + ": " + version);
            }
            if (!seenVersions.add(version)) {
                throw new IllegalStateException("Duplicate migration version found: " + version + " (" + migration.getName() + ")");
            }
            chain = chain.thenCompose(v -> isMigrationAppliedAsync(String.valueOf(version), projectId)
                .thenCompose(applied -> {
                    if (!applied) {
                        log.info("Applying migration {} for project {}", version, projectId);
                        MigrationContent content = migration.getContent();
                        long start = System.currentTimeMillis();
                        CompletableFuture<Void> statementChain = CompletableFuture.completedFuture(null);
                        for (Statement statement : content.statements()) {
                            statementChain = statementChain.thenCompose(v2 -> executeStatement(statement));
                        }
                        return statementChain.thenCompose(v2 -> {
                            long duration = System.currentTimeMillis() - start;
                            return recordMigrationAsync(version, projectId, migration.getName(), duration);
                        });
                    } else {
                        log.debug("Migration {} already applied for project {}", version, projectId);
                        return CompletableFuture.completedFuture(null);
                    }
                }));
        }
        return chain;
    }

    private boolean isValidVersion(Integer version) {
        return version != null && version > 0;
    }

    @SuppressWarnings("unchecked")
    private CompletableFuture<Void> executeStatement(Statement statement) {
        StatementExecutor<Statement, ?> executor = (StatementExecutor<Statement, ?>) findExecutor(statement);
        if (executor != null) {
            return executor.executeMigration(statement)
                .thenApply(r -> null)
                .handle((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to execute statement: {} - Error: {}", statement.getClass().getSimpleName(), ex.getMessage(), ex);
                        throw new RuntimeException("Statement execution failed: " + statement.getClass().getSimpleName() + " - " + ex.getMessage(), ex);
                    }
                    return null;
                });
        } else {
            CompletableFuture<Void> failed = new CompletableFuture<>();
            failed.completeExceptionally(new IllegalStateException("No executor found for statement: " + statement.getClass().getSimpleName()));
            return failed;
        }
    }

    private StatementExecutor<?, ?> findExecutor(Statement statement) {
        return executors.stream()
                        .filter(e -> e.supports(statement))
                        .findFirst()
                        .orElse(null);
    }

    public CompletableFuture<Boolean> isMigrationAppliedAsync(String version, String projectId) {
        return client.search(s -> s
                .index(MIGRATION_INDEX)
                .query(q -> q
                        .bool(b -> b
                                .must(m -> m.term(t -> t.field("version").value(version)))
                                .must(m -> m.term(t -> t.field("projectId").value(projectId)))
                        )
                ),
                Object.class
        ).thenApply(response -> {
            TotalHits totalHits = response.hits().total();
            return totalHits != null && totalHits.value() > 0;
        });
    }

    /**
     * Gets the highest migration version that has been applied to a project.
     * 
     * @param projectId the project identifier
     * @return CompletableFuture<Integer> that completes with the highest applied migration version, or null if no migrations have been applied
     */
    public CompletableFuture<Integer> getLastAppliedMigrationVersion(String projectId) {
        return client.search(s -> s
                .index(MIGRATION_INDEX)
                .query(q -> q
                        .term(t -> t.field("projectId").value(projectId))
                )
                .sort(sort -> sort
                        .field(f -> f.field("version").order(co.elastic.clients.elasticsearch._types.SortOrder.Desc))
                )
                .size(1),
                MigrationRecord.class
        ).thenApply(response -> {
            if (response.hits().hits().isEmpty()) {
                return null;
            }
            MigrationRecord record = response.hits().hits().get(0).source();
            return record != null ? record.version() : null;
        });
    }

    private CompletableFuture<Void> recordMigrationAsync(int version, String projectId, String name, long durationMs) {
        return client.index(i -> i
                .index(MIGRATION_INDEX)
                .document(new MigrationRecord(version, projectId, System.currentTimeMillis(), name, durationMs))
                .refresh(Refresh.WaitFor)
        ).thenApply(response -> null);
    }

    // Enhanced record to track migrations by project with timestamp
    public record MigrationRecord(int version, String projectId, long appliedAt, String name, long durationMs) {}
}