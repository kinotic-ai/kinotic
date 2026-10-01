import {Kinotic} from '@kinotic-ai/core'
import {EntityDefinition, MigrationDefinition} from '@kinotic-ai/management-api'
import {
    AdminEntityRepository,
    EntityRepository,
    IAdminEntityRepository,
    IEntityRepository,
    EntitiesRepository,
    TenantSpecificId
} from '@kinotic-ai/persistence'
import {ProjectMigrationService} from '@kinotic-ai/kinotic-cli/dist/internal/ProjectMigrationService.js'
import {ConsoleLogger} from '@kinotic-ai/kinotic-cli/dist/internal/Logger.js'
import * as allure from 'allure-js-commons'
import {afterAll, afterEach, beforeAll, beforeEach, describe, expect, it} from 'vitest'
import {mkdir, rm, writeFile} from 'fs/promises'
import {join} from 'path'
import {Person} from '../domain/Person.js'
import {PersonWithTenant} from '../domain/PersonWithTenant.js'
import {
    E2E_APP_TENANT as APP_TENANT,
    E2E_ORGANIZATION_ID as TEST_ORG_ID,
    createPersonEntityDefinitionIfNotExist,
    deleteEntityDefinition,
    generateRandomString,
    initKinoticAppClient,
    initKinoticClient,
    shutdownKinoticClient,
} from '../TestHelpers.js'

const APP_ID = 'e2e-migration'

interface LocalTestContext {
    /** PersonWithTenant: a SHARED entity that declares its own tenant id field */
    withTenant: EntityDefinition
    /** Person: a SHARED entity that holds its tenant in the platform's tenant field */
    person: EntityDefinition
    projectId: string
    adminPeople: IAdminEntityRepository<PersonWithTenant>
    migrationsDir: string
}

function migration(version: number, content: string): MigrationDefinition {
    return {version, name: `V${version}__migration.sql`, content}
}

describe('Kinotic JS', () => {

    beforeAll(async () => {
        await allure.suite('e2e-tests/native')
        await allure.subSuite('MigrationService')
        await initKinoticClient()
    }, 300000)

    afterAll(async () => {
        await shutdownKinoticClient()
    }, 60000)

    beforeEach<LocalTestContext>(async (context) => {
        // Both entities of one project; a fresh project each time, so each test starts with no migration history
        const projectName = generateRandomString(5)
        context.withTenant = await createPersonEntityDefinitionIfNotExist(TEST_ORG_ID, APP_ID, projectName, true)
        context.person = await createPersonEntityDefinitionIfNotExist(TEST_ORG_ID, APP_ID, projectName, false)
        expect(context.person.projectId).toBe(context.withTenant.projectId)
        context.projectId = context.withTenant.projectId
        // Reads go through the ORGANIZATION user of initKinoticClient, selecting the tenants the migrations wrote
        context.adminPeople = new AdminEntityRepository(TEST_ORG_ID, APP_ID, context.withTenant.name)
        context.migrationsDir = join(process.cwd(), 'test-migrations-' + generateRandomString(8))
        await mkdir(context.migrationsDir, {recursive: true})
    })

    afterEach<LocalTestContext>(async (context) => {
        await rm(context.migrationsDir, {recursive: true, force: true})
        await expect(deleteEntityDefinition(context.withTenant.id as string)).resolves.toBeUndefined()
        await expect(deleteEntityDefinition(context.person.id as string)).resolves.toBeUndefined()
        await expect(Kinotic.entityDefinitions.syncIndex()).resolves.toBeNull()
        await Kinotic.projects.deleteById(context.projectId)
        await expect(Kinotic.projects.syncIndex()).resolves.toBeNull()
        await Kinotic.applications.deleteById(APP_ID)
    })

    it<LocalTestContext>(
        'applies INSERT, UPDATE and DELETE to the rows of the application\'s entity',
        async ({projectId, adminPeople}) => {
            const result = await Kinotic.migrations.executeMigrations({
                projectId,
                migrations: [
                    migration(1, `
                        INSERT INTO PersonWithTenant (id, tenantId, firstName, lastName, age) VALUES ('p-1', 'tenant01', 'Jane', 'Doe', 28) WITH REFRESH;
                        INSERT INTO PersonWithTenant (id, tenantId, firstName, lastName, age) VALUES ('p-2', 'tenant01', 'John', 'Doe', 31) WITH REFRESH;
                        INSERT INTO PersonWithTenant (id, tenantId, firstName, lastName, age) VALUES ('p-1', 'tenant02', 'Ada', 'Lovelace', 36) WITH REFRESH;
                    `),
                    migration(2, `UPDATE PersonWithTenant SET firstName = 'Janet' WHERE id == 'p-1' WITH REFRESH;`),
                    migration(3, `DELETE FROM PersonWithTenant WHERE tenantId == 'tenant02' WITH REFRESH;`)
                ]
            })
            expect(result.errorMessage).toBeFalsy()
            expect(result.success).toBe(true)
            expect(result.migrationsProcessed).toBe(3)

            // Each row landed under its own tenant, reachable by id the way the entity's repository stores it
            await expect(adminPeople.count(['tenant01'])).resolves.toBe(2)
            await expect(adminPeople.count(['tenant02'])).resolves.toBe(0)
            const janet = await adminPeople.findById(new TenantSpecificId('p-1', 'tenant01'))
            expect(janet?.firstName).toBe('Janet')
            expect(janet?.lastName).toBe('Doe')
            const john = await adminPeople.findById(new TenantSpecificId('p-2', 'tenant01'))
            expect(john?.firstName).toBe('John')

            await expect(Kinotic.migrations.getLastAppliedMigrationVersion(projectId)).resolves.toBe(3)
            await expect(Kinotic.migrations.isMigrationApplied(projectId, '2')).resolves.toBe(true)
            await expect(Kinotic.migrations.isMigrationApplied(projectId, '4')).resolves.toBe(false)
        }
    )

    it<LocalTestContext>(
        'stores a row of an entity without its own tenant id field under the platform\'s tenant field',
        async ({projectId, person}) => {
            const result = await Kinotic.migrations.executeMigrations({
                projectId,
                migrations: [
                    migration(1, `INSERT INTO Person (id, tenantId, firstName, lastName) VALUES ('p-1', '${APP_TENANT}', 'Grace', 'Hopper') WITH REFRESH;`)
                ]
            })
            expect(result.errorMessage).toBeFalsy()
            expect(result.success).toBe(true)

            // The application's own user of that tenant finds the row the way it finds one it saved itself
            const appKinotic = await initKinoticAppClient(APP_ID, APP_TENANT)
            try {
                const people: IEntityRepository<Person> = new EntityRepository(TEST_ORG_ID, APP_ID, person.name, new EntitiesRepository(appKinotic))
                const grace = await people.findById('p-1')
                expect(grace?.firstName).toBe('Grace')
                await expect(people.count()).resolves.toBe(1)
            } finally {
                await appKinotic.disconnect()
            }
        }
    )

    it<LocalTestContext>(
        'rejects a row of a multi-tenant entity that names no tenant',
        async ({projectId}) => {
            const result = await Kinotic.migrations.executeMigrations({
                projectId,
                migrations: [
                    migration(1, `INSERT INTO PersonWithTenant (id, firstName, lastName) VALUES ('p-1', 'Jane', 'Doe') WITH REFRESH;`)
                ]
            })
            expect(result.success).toBe(false)
            expect(result.errorMessage).toContain('tenantId')
            await expect(Kinotic.migrations.getLastAppliedMigrationVersion(projectId)).resolves.toBeNull()
        }
    )

    it<LocalTestContext>(
        'rejects a statement that does not act on the application\'s entities',
        async ({projectId}) => {
            for (const content of [
                'CREATE TABLE users (id KEYWORD, name TEXT);',
                'ALTER TABLE PersonWithTenant ADD COLUMN nickname KEYWORD;'
            ]) {
                const result = await Kinotic.migrations.executeMigrations({projectId, migrations: [migration(1, content)]})
                expect(result.success).toBe(false)
                expect(result.errorMessage).toContain('is not allowed')
            }
            await expect(Kinotic.migrations.getLastAppliedMigrationVersion(projectId)).resolves.toBeNull()
        }
    )

    it<LocalTestContext>(
        'rejects a name that is not one of the application\'s published entities',
        async ({projectId}) => {
            // A platform table and a plain unknown name alike: a migration cannot address storage by name
            for (const name of ['kinotic_application', 'users']) {
                const result = await Kinotic.migrations.executeMigrations({
                    projectId,
                    migrations: [migration(1, `DELETE FROM ${name} WHERE id == 'x' WITH REFRESH;`)]
                })
                expect(result.success).toBe(false)
                expect(result.errorMessage).toContain(`has no published entity named ${name}`)
            }
            await expect(Kinotic.migrations.getLastAppliedMigrationVersion(projectId)).resolves.toBeNull()
        }
    )

    it<LocalTestContext>(
        'resolves the whole run before anything runs',
        async ({projectId, adminPeople}) => {
            // V1 is valid on its own; V2 names an unknown entity, so V1 must not have run either
            const result = await Kinotic.migrations.executeMigrations({
                projectId,
                migrations: [
                    migration(1, `INSERT INTO PersonWithTenant (id, tenantId, firstName, lastName) VALUES ('p-1', 'tenant01', 'Jane', 'Doe') WITH REFRESH;`),
                    migration(2, `DELETE FROM nothing WHERE id == 'x';`)
                ]
            })
            expect(result.success).toBe(false)
            await expect(adminPeople.count(['tenant01'])).resolves.toBe(0)
            await expect(Kinotic.migrations.getLastAppliedMigrationVersion(projectId)).resolves.toBeNull()
        }
    )

    it<LocalTestContext>(
        'answers only for a project of the caller\'s organization',
        async () => {
            const unknownProject = 'not-a-project-' + generateRandomString(5)
            await expect(Kinotic.migrations.getLastAppliedMigrationVersion(unknownProject)).rejects.toThrow(/not found/)
            await expect(Kinotic.migrations.isMigrationApplied(unknownProject, '1')).rejects.toThrow(/not found/)
            const result = await Kinotic.migrations.executeMigrations({
                projectId: unknownProject,
                migrations: [migration(1, `DELETE FROM PersonWithTenant WHERE id == 'x';`)]
            })
            expect(result.success).toBe(false)
            expect(result.errorMessage).toContain('not found')
        }
    )

    it<LocalTestContext>(
        'fails the run on a syntax error without recording anything',
        async ({projectId}) => {
            const result = await Kinotic.migrations.executeMigrations({
                projectId,
                migrations: [migration(1, 'INVALID SQL STATEMENT THAT SHOULD FAIL;')]
            })
            expect(result.success).toBe(false)
            expect(result.errorMessage).toContain('V1__migration.sql')
            expect(result.migrationsProcessed).toBe(0)
            await expect(Kinotic.migrations.getLastAppliedMigrationVersion(projectId)).resolves.toBeNull()
        }
    )

    it<LocalTestContext>(
        'applies the migration files the CLI finds, in version order, skipping the applied ones',
        async ({projectId, adminPeople, migrationsDir}) => {
            const projectMigrationService = new ProjectMigrationService(new ConsoleLogger())
            await writeFile(join(migrationsDir, 'V2__second.sql'),
                            `INSERT INTO PersonWithTenant (id, tenantId, firstName, lastName) VALUES ('p-2', 'tenant01', 'John', 'Doe') WITH REFRESH;`)
            await writeFile(join(migrationsDir, 'V1__first.sql'),
                            `INSERT INTO PersonWithTenant (id, tenantId, firstName, lastName) VALUES ('p-1', 'tenant01', 'Jane', 'Doe') WITH REFRESH;`)
            await writeFile(join(migrationsDir, 'README.md'), '# not a migration')

            await expect(projectMigrationService.applyMigrations(projectId, migrationsDir, true)).resolves.toBeUndefined()
            await expect(adminPeople.count(['tenant01'])).resolves.toBe(2)
            await expect(Kinotic.migrations.getLastAppliedMigrationVersion(projectId)).resolves.toBe(2)

            // A second run applies only the new file: the applied ones would otherwise insert their rows again
            await writeFile(join(migrationsDir, 'V10__tenth.sql'),
                            `UPDATE PersonWithTenant SET lastName = 'Roe' WHERE tenantId == 'tenant01' WITH REFRESH;`)
            await expect(projectMigrationService.applyMigrations(projectId, migrationsDir, true)).resolves.toBeUndefined()
            await expect(adminPeople.count(['tenant01'])).resolves.toBe(2)
            await expect(Kinotic.migrations.getLastAppliedMigrationVersion(projectId)).resolves.toBe(10)
            const jane = await adminPeople.findById(new TenantSpecificId('p-1', 'tenant01'))
            expect(jane?.lastName).toBe('Roe')
        }
    )
})
