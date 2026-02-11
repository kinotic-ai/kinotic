package org.kinotic.structures.api.domain;

import java.util.List;

/**
 * Request object for executing migrations on a project.
 * Contains the project identifier and the list of migrations to execute.
 */
public record MigrationRequest(
    String projectId,
    List<MigrationDefinition> migrations
) {
    
    public MigrationRequest {
        if (projectId == null || projectId.trim().isEmpty()) {
            throw new IllegalArgumentException("Project ID cannot be null or empty");
        }
        if (migrations == null || migrations.isEmpty()) {
            throw new IllegalArgumentException("Migrations list cannot be null or empty");
        }
    }
}
