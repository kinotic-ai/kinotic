package org.mindignited.structures.api.domain;

/**
 * Represents a migration definition that can be sent via the API.
 * This is the API representation of a migration, containing the version, name, and file content.
 */
public record MigrationDefinition(
    Integer version,
    String name,
    String content
) {
    
    public MigrationDefinition {
        if (version == null || version <= 0) {
            throw new IllegalArgumentException("Migration version must be a positive integer");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Migration name cannot be null or empty");
        }
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("Migration content cannot be null or empty");
        }
    }
}
