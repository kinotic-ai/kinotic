import {Kinotic} from '@kinotic-ai/core'
import {AdminEntityRepository, EntityRepository, IAdminEntityRepository, IEntityRepository} from '@kinotic-ai/persistence'
import {EntityDefinition} from '@kinotic-ai/management-api'
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
    initKinoticClient,
    shutdownKinoticClient,
} from '../TestHelpers.js'

const APP_ID = 'e2e-admin-named-query'

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

})