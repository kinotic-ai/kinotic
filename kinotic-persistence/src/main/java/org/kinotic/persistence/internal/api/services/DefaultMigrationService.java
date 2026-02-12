package org.kinotic.persistence.internal.api.services;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.kinotic.persistence.api.domain.MigrationDefinition;
import org.kinotic.persistence.api.domain.MigrationRequest;
import org.kinotic.persistence.api.domain.MigrationResult;
import org.kinotic.persistence.api.services.MigrationService;
import org.kinotic.persistence.sql.domain.Migration;
import org.kinotic.persistence.sql.domain.MigrationContent;
import org.kinotic.persistence.sql.executor.MigrationExecutor;
import org.kinotic.persistence.sql.parsers.MigrationParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * Implementation of MigrationService that allows external clients to execute migrations
 * through the Structures API using the existing MigrationExecutor infrastructure.
 */
@Component
@RequiredArgsConstructor
public class DefaultMigrationService implements MigrationService {
    
    private static final Logger log = LoggerFactory.getLogger(DefaultMigrationService.class);
    
    private final MigrationExecutor migrationExecutor;
    private final MigrationParser migrationParser;

    @Override
    public CompletableFuture<MigrationResult> executeMigrations(MigrationRequest migrationRequest) {
        log.debug("Executing {} migrations for project {}", 
                migrationRequest.migrations().size(), 
                migrationRequest.projectId());
        
        try {
            // Convert API migration definitions to internal Migration objects
            List<Migration> migrations = convertToInternalMigrations(migrationRequest.migrations());
            
            return migrationExecutor.executeProjectMigrations(migrations, migrationRequest.projectId())
                .thenApply(result -> {
                    log.debug("Successfully executed {} migrations for project {}", 
                            migrations.size(), 
                            migrationRequest.projectId());
                    
                    return MigrationResult.success(
                        migrationRequest.projectId(), 
                        migrations.size()
                    );
                })
                .exceptionally(throwable -> {
                    log.debug("Failed to execute migrations for project {}: {}", 
                             migrationRequest.projectId(), throwable.getMessage(), throwable);
                    return MigrationResult.failure(migrationRequest.projectId(), throwable.getMessage());
                });
            
        } catch (Exception e) {
            log.debug("Failed to execute migrations for project {}: {}", 
                     migrationRequest.projectId(), e.getMessage(), e);
            return CompletableFuture.completedFuture(
                MigrationResult.failure(migrationRequest.projectId(), e.getMessage())
            );
        }
    }

    @Override
    public CompletableFuture<Integer> getLastAppliedMigrationVersion(String projectId) {
        // Query Elasticsearch for the highest migration version applied to this project
        return migrationExecutor.getLastAppliedMigrationVersion(projectId);
    }

    @Override
    public CompletableFuture<Boolean> isMigrationApplied(String projectId, String version) {
        return migrationExecutor.isMigrationAppliedAsync(version, projectId);
    }
    
    /**
     * Converts API migration definitions to internal Migration objects.
     */
    private List<Migration> convertToInternalMigrations(List<MigrationDefinition> definitions) {
        return definitions.stream()
            .map(this::convertToInternalMigration)
            .toList();
    }
    
    /**
     * Converts a single API migration definition to an internal Migration object.
     */
    private Migration convertToInternalMigration(MigrationDefinition definition) {
        return new ProjectMigration(definition, migrationParser);
    }
    
    /**
     * Internal Migration implementation that wraps API migration definitions for project migrations.
     */
    private static class ProjectMigration implements Migration {
        private final MigrationDefinition definition;
        private final MigrationParser parser;
        private MigrationContent cachedContent;
        
        public ProjectMigration(MigrationDefinition definition, MigrationParser parser) {
            this.definition = definition;
            this.parser = parser;
        }
        
        @Override
        public Integer getVersion() {
            return definition.version();
        }
        
        @Override
        public String getName() {
            return definition.name();
        }
        
        @Override
        public MigrationContent getContent() {
            if (cachedContent == null) {
                // Parse the migration file content into Statement objects
                cachedContent = parser.parse(definition.content());
            }
            return cachedContent;
        }
    }
}
