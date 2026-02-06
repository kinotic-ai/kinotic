/**
 * Result of executing migrations on a project.
 * Simple success/failure result with optional error details.
 */
export interface MigrationResult {
    projectId: string;
    success: boolean;
    errorMessage?: string;
    migrationsProcessed: number;
}
