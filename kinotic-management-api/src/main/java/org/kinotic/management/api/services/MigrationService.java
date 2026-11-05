package org.kinotic.management.api.services;

import io.vertx.core.Future;
import org.kinotic.core.api.annotations.Publish;
import org.kinotic.idl.api.annotations.AuthzCheck;
import org.kinotic.idl.api.annotations.AuthzResource;
import org.kinotic.idl.api.utils.AuthzConstants;
import org.kinotic.management.api.model.MigrationRequest;
import org.kinotic.management.api.model.MigrationResult;

/**
 * Service for executing project-specific migrations.
 * This service allows external clients to apply their own migrations to projects.
 */
@Publish
@AuthzResource(value = ProjectService.RESOURCE_TYPE, parent = AuthzConstants.APPLICATION_TYPE)
public interface MigrationService {

    /**
     * Executes migrations for a specific project.
     *
     * @param migrationRequest the request containing migrations and project information
     * @return a future that completes with the migration result
     */
    @AuthzCheck(permission = AuthzConstants.CAN_EDIT, resourceId = "{migrationRequest.projectId}")
    Future<MigrationResult> executeMigrations(MigrationRequest migrationRequest);

    /**
     * Gets the highest migration version that has been applied to a project.
     * This allows clients to determine where to start applying new migrations.
     *
     * @param projectId the project identifier
     * @return a future that completes with the highest applied migration version, or null if no migrations have been applied
     */
    Future<Integer> getLastAppliedMigrationVersion(String projectId);

    /**
     * Checks if a specific migration version has been applied to a project.
     *
     * @param projectId the project identifier
     * @param version the migration version to check
     * @return a future that completes with true if the migration has been applied, false otherwise
     */
    @AuthzCheck(permission = AuthzConstants.CAN_READ)
    Future<Boolean> isMigrationApplied(String projectId, String version);
}
