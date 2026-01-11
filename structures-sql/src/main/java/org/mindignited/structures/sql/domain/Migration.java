package org.mindignited.structures.sql.domain;

/**
 * Represents a migration file abstraction for the migration system.
 * <p>
 * Implementations provide access to the migration version and the migration content itself.
 * The version should be available without fully parsing the migration content (e.g., from the filename or metadata).
 * <p>
 * The {@link #getContent()} method may perform lazy loading or parsing of the migration content as an implementation detail.
 * This allows efficient handling of large numbers of migrations, as only unapplied migrations need to be fully loaded.
 * <p>
 * Implementations may represent migrations from files, in-memory sources, remote locations, etc.
 */
public interface Migration {
    /**
     * @return the numeric version for this migration (e.g., 1 for V1__desc)
     */
    Integer getVersion();

    /**
     * @return the name of this migration (usually the filename or logical name)
     */
    String getName();

    /**
     * @return the parsed MigrationContent object for this migration. May be lazily loaded or parsed.
     */
    MigrationContent getContent();
} 