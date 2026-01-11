import {AdminEntityService, IAdminEntityService, IEntityService, Structure, Structures} from '@mindignited/structures-api'
import * as allure from 'allure-js-commons'
import {afterAll, afterEach, beforeAll, beforeEach, describe, expect, it} from 'vitest'
import {WebSocket} from 'ws'
import {PersonWithTenant} from '../domain/PersonWithTenant.js'
import {
    createPersonStructureIfNotExist,
    createSchema,
    createTestPeopleWithTenantAndVerify,
    deleteStructure,
    generateRandomString,
    initContinuumClient,
    shutdownContinuumClient,
} from '../TestHelpers.js'

Object.assign(global, {WebSocket})

interface LocalTestContext {
    structure: Structure
    applicationIdUsed: string
    projectIdUsed: string
    adminEntityService: IAdminEntityService<PersonWithTenant>
    entityService: IEntityService<PersonWithTenant>
}

describe('End To End Tests', () => {

    beforeAll(async () => {
        await allure.suite('Typescript Client')
        await allure.subSuite('Admin Named Query Tests')
        await initContinuumClient()
    }, 300000)

    afterAll(async () => {
        await shutdownContinuumClient()
    }, 60000)

    beforeEach<LocalTestContext>(async (context) => {
        context.applicationIdUsed = generateRandomString(10)
        context.projectIdUsed = generateRandomString(5)
        context.structure = await createPersonStructureIfNotExist(context.applicationIdUsed, context.projectIdUsed, true)
        expect(context.structure).toBeDefined()
        context.adminEntityService = new AdminEntityService(context.structure.applicationId, context.structure.name)
        expect(context.adminEntityService).toBeDefined()
        context.entityService = Structures.createEntityService(context.structure.applicationId, context.structure.name)
        expect(context.entityService).toBeDefined()
    })

    afterEach<LocalTestContext>(async (context) => {
        await expect(deleteStructure(context.structure.id as string)).resolves.toBeUndefined()
        await expect(Structures.getStructureService().syncIndex()).resolves.toBeNull()
        await Structures.getProjectService().deleteById(context.structure.projectId)
        await Structures.getApplicationService().deleteById(context.structure.applicationId)
    })

    it<LocalTestContext>(
        'Aggregate With Parameter and Tenant Selection Test',
        async ({entityService, adminEntityService, applicationIdUsed, projectIdUsed}) => {
            // Create people
            await createTestPeopleWithTenantAndVerify(adminEntityService, entityService, 'tenant01', 100)
            await createTestPeopleWithTenantAndVerify(adminEntityService, entityService, 'tenant02', 100)

            // This wil get any NamedQueries defined in the EntityServices
            const {namedQueriesDefinition} = await createSchema(applicationIdUsed, projectIdUsed, 'PersonWithTenant')

            const namedQueriesService = Structures.getNamedQueriesService()
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