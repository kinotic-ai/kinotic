import { MigrationDefinition } from './MigrationDefinition';

/**
 * Request object for executing migrations on a project.
 * Contains the project identifier and the list of migrations to execute.
 */
export interface MigrationRequest {
    projectId: string;
    migrations: MigrationDefinition[];
}
