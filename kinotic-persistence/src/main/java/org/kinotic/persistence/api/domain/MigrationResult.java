package org.kinotic.persistence.api.domain;

/**
 * Result of executing migrations on a project.
 * Simple success/failure result with optional error details.
 */
public record MigrationResult(
    String projectId,
    boolean success,
    String errorMessage,
    int migrationsProcessed
) {
    
    /**
     * Creates a successful migration result.
     */
    public static MigrationResult success(String projectId, int migrationsProcessed) {
        return new MigrationResult(projectId, true, null, migrationsProcessed);
    }
    
    /**
     * Creates a failed migration result.
     */
    public static MigrationResult failure(String projectId, String errorMessage) {
        return new MigrationResult(projectId, false, errorMessage, 0);
    }
}
