import {Kinotic} from '@kinotic-ai/core'
import {AdminEntityRepository, IAdminEntityRepository, IEntityRepository, EntityRepository} from '@kinotic-ai/persistence'
import {EntityDefinition} from '@kinotic-ai/os-api'
import * as allure from 'allure-js-commons'
import {afterAll, afterEach, beforeAll, beforeEach, describe, expect, it} from 'vitest'
import {PersonWithTenant} from '../domain/PersonWithTenant.js'
import {
    createPersonEntityDefinitionIfNotExist,
    createSchema,
    createTestPeopleWithTenantAndVerify,
    deleteEntityDefinition,
    generateRandomString,
    initKinoticClient,
    shutdownKinoticClient,
} from '../TestHelpers.js'

interface LocalTestContext {
    entityDefinition: EntityDefinition
    applicationIdUsed: string
    projectIdUsed: string
    adminEntityService: IAdminEntityRepository<PersonWithTenant>
    entityService: IEntityRepository<PersonWithTenant>
}

describe('End To End Tests', () => {

    beforeAll(async () => {
        await allure.suite('Typescript Client')
        await allure.subSuite('Admin Named Query Tests')
        await initKinoticClient()
    }, 300000)

    afterAll(async () => {
        await shutdownKinoticClient()
    }, 60000)

    beforeEach<LocalTestContext>(async (context) => {
        context.applicationIdUsed = generateRandomString(10)
        context.projectIdUsed = generateRandomString(5)
        context.entityDefinition = await createPersonEntityDefinitionIfNotExist(context.applicationIdUsed, context.projectIdUsed, true)
        expect(context.entityDefinition).toBeDefined()
        context.adminEntityService = new AdminEntityRepository(context.entityDefinition.applicationId, context.entityDefinition.name)
        expect(context.adminEntityService).toBeDefined()
        context.entityService = new EntityRepository(context.entityDefinition.applicationId, context.entityDefinition.name)
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
            const {namedQueriesDefinition} = await createSchema(applicationIdUsed, projectIdUsed, 'PersonWithTenant')

            const namedQueriesService = Kinotic.namedQueriesDefinitions
            await namedQueriesService.save(namedQueriesDefinition)

            const countResult: any = await entityService.namedQuery('adminCountByLastName',
                                                                    [
                                                                        {key: 'lastName', value: 'Doe'},
                                                                        {key: 'tenantSelection', value: ['tenant01', 'tenant02']}
                                                                    ])

            expect(countResult).toBeDefined()
            expect(countResult).toHaveLength(1)
            expect(countResult[0]).toBeDefined()
            expect(countResult[0].count).toBe(100)

            const countResult2: any = await entityService.namedQuery('adminCountByLastName',
                                                                     [
                                                                         {key: 'lastName', value: 'Doe'},
                                                                         {key: 'tenantSelection', value: ['tenant01']}
                                                                     ])

            expect(countResult2).toBeDefined()
            expect(countResult2).toHaveLength(1)
            expect(countResult2[0]).toBeDefined()
            expect(countResult2[0].count).toBe(50)

            const countResult3: any = await entityService.namedQuery('adminCountByLastName',
                                                                    [
                                                                        {key: 'lastName', value: 'Doe'},
                                                                        {key: 'tenantSelection', value: ['tenant02']}
                                                                    ])

            expect(countResult3).toBeDefined()
            expect(countResult3).toHaveLength(1)
            expect(countResult3[0]).toBeDefined()
            expect(countResult3[0].count).toBe(50)
        }
    )

})