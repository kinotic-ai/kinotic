import {Kinotic} from '@kinotic-ai/core'
import {ArrayC3Type, FunctionDefinition, LongC3Type, ObjectC3Type, StringC3Type} from '@kinotic-ai/idl'
import {AdminEntityRepository, EntitiesRepository, EntityRepository, IAdminEntityRepository, IEntityRepository, TenantSpecificId} from '@kinotic-ai/persistence'
import {EntityDefinition, NamedQueriesDefinition, QueryDecorator, TenantSelectionC3Type} from '@kinotic-ai/management-api'
import * as allure from 'allure-js-commons'
import {afterAll, afterEach, beforeAll, beforeEach, describe, expect, it} from 'vitest'
import {PersonWithTenant} from '../domain/PersonWithTenant.js'
import {
    E2E_ORGANIZATION_ID as TEST_ORG_ID,
    createPersonEntityDefinitionIfNotExist,
    createSchema,
    createTestPeopleWithTenantAndVerify,
    deleteEntityDefinition,
    generateRandomString,
    initKinoticAppClient,
    initKinoticClient,
    shutdownKinoticClient,
} from '../TestHelpers.js'

// The app user for (APP_ID, 'tenant01') is seeded by the V3__e2e_app_fixtures migration.
const APP_ID = 'e2e-admin-named-query'

/** The row every write returns: the number of documents it wrote. */
function writeCount(applicationId: string): ArrayC3Type {
    return new ArrayC3Type(new ObjectC3Type('WriteCount', applicationId).addProperty('count', new LongC3Type()))
}

/** A query of the tenant-scoped repository: it takes the parameters given and acts within the participant's tenant. */
function query(name: string, statement: string, returnType: ArrayC3Type, ...parameters: string[]): FunctionDefinition {
    const ret = new FunctionDefinition(name, [new QueryDecorator(statement)])
    for (const parameter of parameters) {
        ret.addParameter(parameter, new StringC3Type())
    }
    ret.returnType = returnType
    return ret
}

/** A query of the admin repository: the same, acting within the tenants its tenant selection names. */
function adminQuery(name: string, statement: string, returnType: ArrayC3Type, ...parameters: string[]): FunctionDefinition {
    const ret = query(name, statement, returnType, ...parameters)
    ret.addParameter('tenantSelection', new TenantSelectionC3Type())
    return ret
}

interface LocalTestContext {
    entityDefinition: EntityDefinition
    applicationIdUsed: string
    projectIdUsed: string
    adminEntityService: IAdminEntityRepository<PersonWithTenant>
    entityService: IEntityRepository<PersonWithTenant>
}

describe('Kinotic JS', () => {

    beforeAll(async () => {
        await allure.suite('e2e-tests/native')
        await allure.subSuite('AdminNamedQuery')
        await initKinoticClient()
    }, 300000)

    afterAll(async () => {
        await shutdownKinoticClient()
    }, 60000)

    beforeEach<LocalTestContext>(async (context) => {
        context.applicationIdUsed = APP_ID
        context.projectIdUsed = generateRandomString(5)
        context.entityDefinition = await createPersonEntityDefinitionIfNotExist(TEST_ORG_ID, context.applicationIdUsed, context.projectIdUsed, true)
        expect(context.entityDefinition).toBeDefined()
        // The repositories act through the ORGANIZATION user of initKinoticClient: an APPLICATION user is
        // confined to its own tenant, and this test saves and selects several
        context.adminEntityService = new AdminEntityRepository(
            context.entityDefinition.organizationId,
            context.entityDefinition.applicationId,
            context.entityDefinition.name
        )
        expect(context.adminEntityService).toBeDefined()
        context.entityService = new EntityRepository(
            context.entityDefinition.organizationId,
            context.entityDefinition.applicationId,
            context.entityDefinition.name
        )
        expect(context.entityService).toBeDefined()
    })

    afterEach<LocalTestContext>(async (context) => {
        await expect(deleteEntityDefinition(context.entityDefinition.id as string)).resolves.toBeUndefined()
        await expect(Kinotic.entityDefinitions.syncIndex()).resolves.toBeNull()
        await Kinotic.projects.deleteById(context.entityDefinition.projectId)
        await expect(Kinotic.projects.syncIndex()).resolves.toBeNull()
        await Kinotic.applications.deleteById(context.entityDefinition.applicationId)
    })

    it<LocalTestContext>(
        'Aggregate With Parameter and Tenant Selection Test',
        async ({entityService, adminEntityService, applicationIdUsed, projectIdUsed}) => {
            // Create people
            await createTestPeopleWithTenantAndVerify(adminEntityService, entityService, 'tenant01', 100)
            await createTestPeopleWithTenantAndVerify(adminEntityService, entityService, 'tenant02', 100)

            // This wil get any NamedQueries defined in the EntityServices
            const {namedQueriesDefinition} = await createSchema(TEST_ORG_ID, applicationIdUsed, projectIdUsed, 'PersonWithTenant')

            const namedQueriesService = Kinotic.namedQueriesDefinitions
            await namedQueriesService.saveSync(namedQueriesDefinition)

            const countResult: any = await adminEntityService.namedQuery('adminCountByLastName',
                                                                         [{key: 'lastName', value: 'Doe'}],
                                                                         ['tenant01', 'tenant02'])

            expect(countResult).toBeDefined()
            expect(countResult).toHaveLength(1)
            expect(countResult[0]).toBeDefined()
            expect(countResult[0].count).toBe(100)

            const countResult2: any = await adminEntityService.namedQuery('adminCountByLastName',
                                                                          [{key: 'lastName', value: 'Doe'}],
                                                                          ['tenant01'])

            expect(countResult2).toBeDefined()
            expect(countResult2).toHaveLength(1)
            expect(countResult2[0]).toBeDefined()
            expect(countResult2[0].count).toBe(50)

            const countResult3: any = await adminEntityService.namedQuery('adminCountByLastName',
                                                                         [{key: 'lastName', value: 'Doe'}],
                                                                         ['tenant02'])

            expect(countResult3).toBeDefined()
            expect(countResult3).toHaveLength(1)
            expect(countResult3[0]).toBeDefined()
            expect(countResult3[0].count).toBe(50)
        }
    )

    it<LocalTestContext>(
        'confines a query of the admin repository to the tenants it selects',
        async ({entityService, adminEntityService, entityDefinition, applicationIdUsed, projectIdUsed}) => {
            await createTestPeopleWithTenantAndVerify(adminEntityService, entityService, 'tenant01', 100)
            await createTestPeopleWithTenantAndVerify(adminEntityService, entityService, 'tenant02', 100)

            const people = new ArrayC3Type(entityDefinition.schema)
            await Kinotic.namedQueriesDefinitions.saveSync(new NamedQueriesDefinition(entityDefinition.id as string,
                TEST_ORG_ID, applicationIdUsed, projectIdUsed, entityDefinition.name, [
                    adminQuery('adminFindByLastName', 'SELECT * FROM PersonWithTenant WHERE lastName = :lastName', people, 'lastName'),
                    adminQuery('adminRenameByLastName', 'UPDATE PersonWithTenant SET firstName = :firstName WHERE lastName = :lastName WITH REFRESH', writeCount(applicationIdUsed), 'firstName', 'lastName'),
                    adminQuery('adminDeleteByLastName', 'DELETE FROM PersonWithTenant WHERE lastName = :lastName WITH REFRESH', writeCount(applicationIdUsed), 'lastName'),
                    adminQuery('adminAddPerson', 'INSERT INTO PersonWithTenant (id, firstName, lastName) VALUES (:id, :firstName, :lastName) WITH REFRESH', writeCount(applicationIdUsed), 'id', 'firstName', 'lastName')
                ]))

            const doe = [{key: 'lastName', value: 'Doe'}]
            const both: PersonWithTenant[] = await adminEntityService.namedQuery('adminFindByLastName', doe, ['tenant01', 'tenant02'])
            expect(both).toHaveLength(100)
            expect(new Set(both.map(person => person.tenantId))).toEqual(new Set(['tenant01', 'tenant02']))
            const one: PersonWithTenant[] = await adminEntityService.namedQuery('adminFindByLastName', doe, ['tenant01'])
            expect(one).toHaveLength(50)
            expect(one.every(person => person.tenantId === 'tenant01')).toBe(true)

            // a write acts on one tenant, and only on that tenant's rows
            const rename = [{key: 'firstName', value: 'Jane'}, ...doe]
            await expect(adminEntityService.namedQuery('adminRenameByLastName', rename, ['tenant01', 'tenant02'])).rejects.toThrow(/one tenant/)
            await expect(adminEntityService.namedQuery('adminRenameByLastName', rename, ['tenant01'])).resolves.toEqual([{count: 50}])
            const renamed: PersonWithTenant[] = await adminEntityService.namedQuery('adminFindByLastName', doe, ['tenant01'])
            expect(renamed.every(person => person.firstName === 'Jane')).toBe(true)
            const untouched: PersonWithTenant[] = await adminEntityService.namedQuery('adminFindByLastName', doe, ['tenant02'])
            expect(untouched.every(person => person.firstName === 'John')).toBe(true)

            await expect(adminEntityService.namedQuery('adminDeleteByLastName', doe, ['tenant02'])).resolves.toEqual([{count: 50}])
            await expect(adminEntityService.count(['tenant01'])).resolves.toBe(100)
            await expect(adminEntityService.count(['tenant02'])).resolves.toBe(50)

            // an inserted row belongs to the selected tenant and is stored the way the repository stores one
            const add = [{key: 'id', value: 'p-new'}, {key: 'firstName', value: 'Ada'}, {key: 'lastName', value: 'Lovelace'}]
            await expect(adminEntityService.namedQuery('adminAddPerson', add, ['tenant01'])).resolves.toEqual([{count: 1}])
            const ada = await adminEntityService.findById(new TenantSpecificId('p-new', 'tenant01'))
            expect(ada?.firstName).toBe('Ada')
            expect(ada?.tenantId).toBe('tenant01')
            await expect(adminEntityService.findById(new TenantSpecificId('p-new', 'tenant02'))).resolves.toBeNull()
        }
    )

    it<LocalTestContext>(
        'confines a query of an application participant to its own tenant',
        async ({entityService, adminEntityService, entityDefinition, applicationIdUsed, projectIdUsed}) => {
            await createTestPeopleWithTenantAndVerify(adminEntityService, entityService, 'tenant01', 100)
            await createTestPeopleWithTenantAndVerify(adminEntityService, entityService, 'tenant02', 100)

            const people = new ArrayC3Type(entityDefinition.schema)
            await Kinotic.namedQueriesDefinitions.saveSync(new NamedQueriesDefinition(entityDefinition.id as string,
                TEST_ORG_ID, applicationIdUsed, projectIdUsed, entityDefinition.name, [
                    query('findByLastName', 'SELECT * FROM PersonWithTenant WHERE lastName = :lastName', people, 'lastName'),
                    query('renameByLastName', 'UPDATE PersonWithTenant SET firstName = :firstName WHERE lastName = :lastName WITH REFRESH', writeCount(applicationIdUsed), 'firstName', 'lastName'),
                    query('deleteByLastName', 'DELETE FROM PersonWithTenant WHERE lastName = :lastName WITH REFRESH', writeCount(applicationIdUsed), 'lastName'),
                    query('addPerson', 'INSERT INTO PersonWithTenant (id, firstName, lastName, tenantId) VALUES (:id, :firstName, :lastName, :tenantId) WITH REFRESH', writeCount(applicationIdUsed), 'id', 'firstName', 'lastName', 'tenantId'),
                    adminQuery('adminFindByLastName', 'SELECT * FROM PersonWithTenant WHERE lastName = :lastName', people, 'lastName')
                ]))

            const appKinotic = await initKinoticAppClient(applicationIdUsed, 'tenant01')
            try {
                const myPeople: IEntityRepository<PersonWithTenant> = new EntityRepository(TEST_ORG_ID, applicationIdUsed, entityDefinition.name, new EntitiesRepository(appKinotic))
                const doe = [{key: 'lastName', value: 'Doe'}]

                const found: PersonWithTenant[] = await myPeople.namedQuery('findByLastName', doe)
                expect(found).toHaveLength(50)
                expect(found.every(person => person.tenantId === 'tenant01')).toBe(true)

                await expect(myPeople.namedQuery('renameByLastName', [{key: 'firstName', value: 'Jane'}, ...doe])).resolves.toEqual([{count: 50}])
                const otherTenant: PersonWithTenant[] = await adminEntityService.namedQuery('adminFindByLastName', doe, ['tenant02'])
                expect(otherTenant).toHaveLength(50)
                expect(otherTenant.every(person => person.firstName === 'John')).toBe(true)

                await expect(myPeople.namedQuery('deleteByLastName', doe)).resolves.toEqual([{count: 50}])
                await expect(adminEntityService.count(['tenant01'])).resolves.toBe(50)
                await expect(adminEntityService.count(['tenant02'])).resolves.toBe(100)

                // a row may name the participant's own tenant, never another
                const add = [{key: 'id', value: 'p-new'}, {key: 'firstName', value: 'Ada'}, {key: 'lastName', value: 'Lovelace'}]
                await expect(myPeople.namedQuery('addPerson', [...add, {key: 'tenantId', value: 'tenant02'}])).rejects.toThrow(/belongs to tenant01/)
                await expect(myPeople.namedQuery('addPerson', [...add, {key: 'tenantId', value: 'tenant01'}])).resolves.toEqual([{count: 1}])
                expect((await myPeople.findById('p-new'))?.firstName).toBe('Ada')
            } finally {
                await appKinotic.disconnect()
            }
        }
    )

})
