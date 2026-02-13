import { Continuum, IServiceProxy } from '@kinotic/continuum-client';
import { MigrationRequest } from './domain/MigrationRequest';
import { MigrationResult } from './domain/MigrationResult';

/**
 * Service for executing project-specific migrations through the Structures API.
 * This service allows external clients to apply their own migrations to projects.
 */
export interface IMigrationService {

    /**
     * Executes migrations for a specific project.
     *
     * @param migrationRequest the request containing migrations and project information
     * @returns a promise that resolves with the migration result
     */
    executeMigrations(migrationRequest: MigrationRequest): Promise<MigrationResult>;

    /**
     * Gets the highest migration version that has been applied to a project.
     * This allows clients to determine where to start applying new migrations.
     *
     * @param projectId the project identifier
     * @returns a promise that resolves with the highest applied migration version, or null if no migrations have been applied
     */
    getLastAppliedMigrationVersion(projectId: string): Promise<number | null>;

    /**
     * Checks if a specific migration version has been applied to a project.
     *
     * @param projectId the project identifier
     * @param version the migration version to check
     * @returns a promise that resolves with true if the migration has been applied, false otherwise
     */
    isMigrationApplied(projectId: string, version: string): Promise<boolean>;
}

export class MigrationService implements IMigrationService {

    private readonly serviceProxy: IServiceProxy;

    constructor() {
        this.serviceProxy = Continuum.serviceProxy('org.kinotic.structures.api.services.MigrationService');
    }

    public executeMigrations(migrationRequest: MigrationRequest): Promise<MigrationResult> {
        return this.serviceProxy.invoke('executeMigrations', [migrationRequest]);
    }

    public getLastAppliedMigrationVersion(projectId: string): Promise<number | null> {
        return this.serviceProxy.invoke('getLastAppliedMigrationVersion', [projectId]);
    }

    public isMigrationApplied(projectId: string, version: string): Promise<boolean> {
        return this.serviceProxy.invoke('isMigrationApplied', [projectId, version]);
    }
}
