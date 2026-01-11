import { IMigrationService, MigrationDefinition, MigrationRequest, Project, Structures } from '@mindignited/structures-api'
import { ProjectMigrationService } from '@mindignited/structures-cli/dist/internal/ProjectMigrationService.js'
import { ConsoleLogger } from '@mindignited/structures-cli/dist/internal/Logger.js'
import * as allure from 'allure-js-commons'
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it } from 'vitest'
import { WebSocket } from 'ws'
import { writeFile, mkdir, rm } from 'fs/promises'
import { join } from 'path'
import { 
    generateRandomString,
    initContinuumClient,
    shutdownContinuumClient
} from '../TestHelpers.js'

Object.assign(global, { WebSocket })

interface LocalTestContext {
    project: Project
    migrationService: IMigrationService
    projectMigrationService: ProjectMigrationService
    testMigrationsDir: string
    applicationId: string
}

describe('Migration Service End To End Tests', () => {

    beforeAll(async () => {
        await allure.suite('Typescript Client')
        await allure.subSuite('Migration Service Tests')
        await initContinuumClient()
    }, 300000)

    afterAll(async () => {
        await shutdownContinuumClient()
    }, 60000)

    beforeEach<LocalTestContext>(async (context) => {
        // Create application and project
        context.applicationId = generateRandomString(10)
        await Structures.getApplicationService().createApplicationIfNotExist(context.applicationId, 'Test Application')
        
        const project = new Project(null, context.applicationId, generateRandomString(5), 'Test Project')
        context.project = await Structures.getProjectService().createProjectIfNotExist(project)
        expect(context.project).toBeDefined()
        expect(context.project.id).toBeDefined()

        // Initialize services
        context.migrationService = Structures.getMigrationService()
        expect(context.migrationService).toBeDefined()

        const logger = new ConsoleLogger()
        context.projectMigrationService = new ProjectMigrationService(logger)

        // Create test migrations directory
        context.testMigrationsDir = join(process.cwd(), 'test-migrations-' + generateRandomString(8))
        await mkdir(context.testMigrationsDir, { recursive: true })
    })

    afterEach<LocalTestContext>(async (context) => {
        // Clean up test migrations directory
        try {
            await rm(context.testMigrationsDir, { recursive: true, force: true })
        } catch (e) {
            // Ignore cleanup errors
        }

        // Clean up project and application
        if (context.project.id) {
            await Structures.getProjectService().deleteById(context.project.id)
        }
        await Structures.getApplicationService().deleteById(context.applicationId)
    })

    it<LocalTestContext>(
        'Test Basic Migration Execution',
        async ({ migrationService, project }) => {
            const migrations: MigrationDefinition[] = [
                {
                    version: 1,
                    name: 'V1__create_users_table.sql',
                    content: 'CREATE TABLE users (id UUID, name TEXT);'
                },
                {
                    version: 2,
                    name: 'V2__add_email_column.sql',
                    content: 'ALTER TABLE users ADD COLUMN email KEYWORD;'
                }
            ]

            const migrationRequest: MigrationRequest = {
                projectId: project.id as string,
                migrations
            }

            // Execute migrations
            const result = await migrationService.executeMigrations(migrationRequest)
            
            expect(result).toBeDefined()
            expect(result.success).toBe(true)
            expect(result.projectId).toBe(project.id)
            expect(result.migrationsProcessed).toBe(2)
            expect(result.errorMessage).toBeUndefined()

            // Verify last applied migration version
            const lastVersion = await migrationService.getLastAppliedMigrationVersion(project.id as string)
            expect(lastVersion).toBe(2)

            // Verify individual migrations were applied
            const isV1Applied = await migrationService.isMigrationApplied(project.id as string, '1')
            const isV2Applied = await migrationService.isMigrationApplied(project.id as string, '2')
            const isV3Applied = await migrationService.isMigrationApplied(project.id as string, '3')
            
            expect(isV1Applied).toBe(true)
            expect(isV2Applied).toBe(true)
            expect(isV3Applied).toBe(false)
        }
    )

    it<LocalTestContext>(
        'Test Migration Service With No Previous Migrations',
        async ({ migrationService, project }) => {
            // Check last applied version when no migrations exist
            const lastVersion = await migrationService.getLastAppliedMigrationVersion(project.id as string)
            expect(lastVersion).toBeNull()

            // Check if specific migration is applied when none exist
            const isApplied = await migrationService.isMigrationApplied(project.id as string, '1')
            expect(isApplied).toBe(false)
        }
    )

    it<LocalTestContext>(
        'Test Incremental Migration Application',
        async ({ migrationService, project }) => {
            // Apply first migration
            const migration1: MigrationDefinition = {
                version: 1,
                name: 'V1__create_users_table.sql',
                content: 'CREATE TABLE users (id UUID, name TEXT);'
            }

            let result = await migrationService.executeMigrations({
                projectId: project.id as string,
                migrations: [migration1]
            })

            expect(result.success).toBe(true)
            expect(result.migrationsProcessed).toBe(1)

            // Verify first migration
            let lastVersion = await migrationService.getLastAppliedMigrationVersion(project.id as string)
            expect(lastVersion).toBe(1)

            // Apply second migration
            const migration2: MigrationDefinition = {
                version: 2,
                name: 'V2__add_email_column.sql',
                content: 'ALTER TABLE users ADD COLUMN email KEYWORD;'
            }

            result = await migrationService.executeMigrations({
                projectId: project.id as string,
                migrations: [migration2]
            })

            expect(result.success).toBe(true)
            expect(result.migrationsProcessed).toBe(1)

            // Verify both migrations
            lastVersion = await migrationService.getLastAppliedMigrationVersion(project.id as string)
            expect(lastVersion).toBe(2)

            const isV1Applied = await migrationService.isMigrationApplied(project.id as string, '1')
            const isV2Applied = await migrationService.isMigrationApplied(project.id as string, '2')
            
            expect(isV1Applied).toBe(true)
            expect(isV2Applied).toBe(true)
        }
    )

    it<LocalTestContext>(
        'Test Empty Migration Request',
        async ({ migrationService, project }) => {
            const result = await migrationService.executeMigrations({
                projectId: project.id as string,
                migrations: []
            })

            expect(result.success).toBe(true)
            expect(result.migrationsProcessed).toBe(0)
            expect(result.projectId).toBe(project.id)

            // Verify no migrations were applied
            const lastVersion = await migrationService.getLastAppliedMigrationVersion(project.id as string)
            expect(lastVersion).toBeNull()
        }
    )

    it<LocalTestContext>(
        'Test ProjectMigrationService With Migration Files',
        async ({ projectMigrationService, project, testMigrationsDir }) => {
            // Create test migration files
            await writeFile(
                join(testMigrationsDir, 'V1__create_users_table.sql'),
                'CREATE TABLE users (id UUID, name TEXT);'
            )
            
            await writeFile(
                join(testMigrationsDir, 'V2__add_email_column.sql'),
                'ALTER TABLE users ADD COLUMN email KEYWORD;'
            )

            await writeFile(
                join(testMigrationsDir, 'V3__add_age_column.sql'),
                'ALTER TABLE users ADD COLUMN age INTEGER;'
            )

            // Apply migrations using ProjectMigrationService
            await expect(
                projectMigrationService.applyMigrations(project.id as string, testMigrationsDir, true)
            ).resolves.toBeUndefined()

            // Verify all migrations were applied
            const migrationService = Structures.getMigrationService()
            const lastVersion = await migrationService.getLastAppliedMigrationVersion(project.id as string)
            expect(lastVersion).toBe(3)

            // Verify individual migrations
            for (let i = 1; i <= 3; i++) {
                const isApplied = await migrationService.isMigrationApplied(project.id as string, i.toString())
                expect(isApplied).toBe(true)
            }
        }
    )

    it<LocalTestContext>(
        'Test ProjectMigrationService With No Migration Directory',
        async ({ projectMigrationService, project }) => {
            const nonExistentDir = '/path/that/does/not/exist'
            
            // Should not throw an error, just log and return
            await expect(
                projectMigrationService.applyMigrations(project.id as string, nonExistentDir, true)
            ).resolves.toBeUndefined()

            // Verify no migrations were applied
            const migrationService = Structures.getMigrationService()
            const lastVersion = await migrationService.getLastAppliedMigrationVersion(project.id as string)
            expect(lastVersion).toBeNull()
        }
    )

    it<LocalTestContext>(
        'Test ProjectMigrationService Skips Already Applied Migrations',
        async ({ projectMigrationService, project, testMigrationsDir }) => {
            // Create test migration files
            await writeFile(
                join(testMigrationsDir, 'V1__create_users_table.sql'),
                'CREATE TABLE users (id UUID, name TEXT);'
            )
            
            await writeFile(
                join(testMigrationsDir, 'V2__add_email_column.sql'),
                'ALTER TABLE users ADD COLUMN email KEYWORD;'
            )

            // Apply migrations first time
            await projectMigrationService.applyMigrations(project.id as string, testMigrationsDir, true)

            // Add a new migration file
            await writeFile(
                join(testMigrationsDir, 'V3__add_age_column.sql'),
                'ALTER TABLE users ADD COLUMN age INTEGER;'
            )

            // Apply migrations again - should only apply the new one
            await projectMigrationService.applyMigrations(project.id as string, testMigrationsDir, true)

            // Verify all migrations are applied
            const migrationService = Structures.getMigrationService()
            const lastVersion = await migrationService.getLastAppliedMigrationVersion(project.id as string)
            expect(lastVersion).toBe(3)

            // Verify individual migrations
            for (let i = 1; i <= 3; i++) {
                const isApplied = await migrationService.isMigrationApplied(project.id as string, i.toString())
                expect(isApplied).toBe(true)
            }
        }
    )

    it<LocalTestContext>(
        'Test ProjectMigrationService With Invalid Migration Filename',
        async ({ projectMigrationService, project, testMigrationsDir }) => {
            // Create migration file with invalid name
            await writeFile(
                join(testMigrationsDir, 'invalid_migration_name.sql'),
                'CREATE TABLE test (id BIGINT);'
            )

            // Should throw an error due to invalid filename format
            await expect(
                projectMigrationService.applyMigrations(project.id as string, testMigrationsDir, true)
            ).rejects.toThrow(/Invalid migration filename format/)
        }
    )

    it<LocalTestContext>(
        'Test ProjectMigrationService Ignores Non-SQL Files',
        async ({ projectMigrationService, project, testMigrationsDir }) => {
            // Create valid migration file
            await writeFile(
                join(testMigrationsDir, 'V1__create_table.sql'),
                'CREATE TABLE users (id UUID);'
            )

            // Create non-SQL files that should be ignored
            await writeFile(
                join(testMigrationsDir, 'README.md'),
                '# Migration Notes'
            )

            await writeFile(
                join(testMigrationsDir, 'config.json'),
                '{"setting": "value"}'
            )

            // Apply migrations - should only process SQL file
            await projectMigrationService.applyMigrations(project.id as string, testMigrationsDir, true)

            // Verify only the SQL migration was applied
            const migrationService = Structures.getMigrationService()
            const lastVersion = await migrationService.getLastAppliedMigrationVersion(project.id as string)
            expect(lastVersion).toBe(1)
        }
    )

    it<LocalTestContext>(
        'Test Migration Version Ordering',
        async ({ projectMigrationService, project, testMigrationsDir }) => {
            // Create migration files out of order in filesystem
            await writeFile(
                join(testMigrationsDir, 'V10__tenth_migration.sql'),
                'CREATE TABLE table10 (id UUID);'
            )
            
            await writeFile(
                join(testMigrationsDir, 'V2__second_migration.sql'),
                'CREATE TABLE table2 (id UUID);'
            )

            await writeFile(
                join(testMigrationsDir, 'V1__first_migration.sql'),
                'CREATE TABLE table1 (id UUID);'
            )

            await writeFile(
                join(testMigrationsDir, 'V5__fifth_migration.sql'),
                'CREATE TABLE table5 (id UUID);'
            )

            // Apply migrations - should be applied in version order
            await projectMigrationService.applyMigrations(project.id as string, testMigrationsDir, true)

            // Verify all migrations were applied in correct order
            const migrationService = Structures.getMigrationService()
            const lastVersion = await migrationService.getLastAppliedMigrationVersion(project.id as string)
            expect(lastVersion).toBe(10)

            // Verify all versions are applied
            for (const version of [1, 2, 5, 10]) {
                const isApplied = await migrationService.isMigrationApplied(project.id as string, version.toString())
                expect(isApplied).toBe(true)
            }
        }
    )

    it<LocalTestContext>(
        'Test Migration Service Error Handling',
        async ({ migrationService, project }) => {
            const invalidMigration: MigrationDefinition = {
                version: 1,
                name: 'V1__invalid_sql.sql',
                content: 'INVALID SQL STATEMENT THAT SHOULD FAIL;'
            }

            const migrationRequest: MigrationRequest = {
                projectId: project.id as string,
                migrations: [invalidMigration]
            }

            // Execute migration with invalid SQL
            const result = await migrationService.executeMigrations(migrationRequest)
            
            // The result should indicate failure
            expect(result).toBeDefined()
            expect(result.success).toBe(false)
            expect(result.projectId).toBe(project.id)
            expect(result.errorMessage).toBeDefined()
            expect(result.migrationsProcessed).toBe(0)

            // Verify no migrations were applied
            const lastVersion = await migrationService.getLastAppliedMigrationVersion(project.id as string)
            expect(lastVersion).toBeNull()
        }
    )

})
