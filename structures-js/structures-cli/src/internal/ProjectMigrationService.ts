import { IMigrationService, MigrationDefinition, MigrationRequest, Structures } from '@mindignited/structures-api'
import { readdir, readFile, access } from 'fs/promises'
import { join } from 'path'
import { constants } from 'fs'
import { Logger } from './Logger.js'

/**
 * Internal service for loading and applying migrations from the local filesystem.
 * Looks for migration files in a 'migrations' folder in the project root.
 */
export class ProjectMigrationService {
    private readonly migrationService: IMigrationService
    private readonly logger: Logger

    constructor(logger: Logger) {
        this.migrationService = Structures.getMigrationService()
        this.logger = logger
    }

    /**
     * Loads migration files from the migrations directory and applies any new ones.
     *
     * @param projectId the project identifier to apply migrations to
     * @param migrationsDir the directory containing migration files (defaults to './migrations')
     * @param verbose whether to enable verbose logging
     */
    async applyMigrations(
        projectId: string,
        migrationsDir: string = './migrations',
        verbose: boolean = false
    ): Promise<void> {
        // Check if migrations directory exists
        try {
            await access(migrationsDir, constants.F_OK)
        } catch {
            this.logger.log('No migrations will be applied since no migrations directory found')
            return
        }

        // Get the last applied migration version
        const lastAppliedVersion = await this.migrationService.getLastAppliedMigrationVersion(projectId)

        // Load only the files that need to be applied
        const migrationsToApply = await this.loadUnappliedMigrations(migrationsDir, lastAppliedVersion)

        if (migrationsToApply.length === 0) {
            this.logVerbose('No migration files found or all migrations already applied', verbose)
            return
        }

        this.logger.log(`Applying ${migrationsToApply.length} new migrations (starting from version ${lastAppliedVersion !== null ? lastAppliedVersion + 1 : 1})`)

        // Apply the migrations
        const migrationRequest: MigrationRequest = {
            projectId,
            migrations: migrationsToApply
        }

        const result = await this.migrationService.executeMigrations(migrationRequest)

        if (result.success) {
            this.logger.log(`Successfully applied ${result.migrationsProcessed} migrations`)
        } else {
            throw new Error(`Migration failed: ${result.errorMessage}`)
        }
    }

    /**
     * Loads only the migration files that need to be applied, checking everything in one pass.
     */
    private async loadUnappliedMigrations(
        migrationsDir: string,
        lastAppliedVersion: number | null
    ): Promise<MigrationDefinition[]> {
        const migrations: MigrationDefinition[] = []

        // Read directory and process files in one pass
        const files = await readdir(migrationsDir)

        for (const file of files) {
            // Skip non-SQL files
            if (!file.endsWith('.sql')) {
                continue
            }

            // Extract version from filename (e.g., V1__create_table.sql -> 1)
            const version = this.extractVersionFromFilename(file)
            if (version === null) {
                throw new Error(`Invalid migration filename format: ${file}. Expected format: V{version}__{description}.sql`)
            }

            // Only load files that need to be applied
            if (lastAppliedVersion === null || version > lastAppliedVersion) {
                const filePath = join(migrationsDir, file)
                const content = await readFile(filePath, 'utf8')

                migrations.push({
                    version,
                    name: file,
                    content
                })
            }
        }

        // Sort by version to ensure proper order
        migrations.sort((a, b) => a.version - b.version)

        return migrations
    }

    /**
     * Extracts the version number from a migration filename.
     * Expected format: V{version}__{description}.sql
     */
    private extractVersionFromFilename(filename: string): number | null {
        const match = filename.match(/^V(\d+)__.*\.sql$/)
        return match ? parseInt(match[1], 10) : null
    }

    private logVerbose(message: string, verbose: boolean): void {
        if (verbose) {
            this.logger.log(message)
        }
    }
}
