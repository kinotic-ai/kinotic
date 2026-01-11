import {Direction, Order, Page, Pageable, Sort} from '@mindignited/continuum-client'
import {
    AdminEntityService,
    IAdminEntityService,
    IEntityService,
    Structure,
    Structures,
    TenantSpecificId
} from '@mindignited/structures-api'
import {afterAll, afterEach, beforeAll, beforeEach, describe, expect, it} from 'vitest'
import {WebSocket} from 'ws'
import {Person} from '../domain/Person.js'
import {PersonWithTenant} from '../domain/PersonWithTenant.js'
import {
    createPersonStructureIfNotExist,
    createTestPeopleWithTenantAndVerify,
    createTestPersonWithTenant,
    deleteStructure,
    generateRandomString,
    initContinuumClient,
    logFailure,
    shutdownContinuumClient,
} from '../TestHelpers.js'
import * as allure from "allure-js-commons";

Object.assign(global, { WebSocket})

interface LocalTestContext {
    structure: Structure
    adminEntityService: IAdminEntityService<PersonWithTenant>
    entityService: IEntityService<PersonWithTenant>
}

describe('End To End Tests', () => {

    beforeAll(async () => {
        await allure.suite('Typescript Client')
        await allure.subSuite('Admin EntityService Tests')
        await initContinuumClient()
    }, 300000)

    afterAll(async () => {
        await shutdownContinuumClient()
    }, 60000)

    beforeEach<LocalTestContext>(async (context) => {
        context.structure = await createPersonStructureIfNotExist(generateRandomString(10), generateRandomString(5), true)
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
        await expect(Structures.getProjectService().syncIndex()).resolves.toBeNull()
        await Structures.getApplicationService().deleteById(context.structure.applicationId)

    })

    it<LocalTestContext>(
        'Test Basic CRUD',
        async ({entityService, adminEntityService}) => {
            // Create a person
            const person = createTestPersonWithTenant(1, 'tenant01')
            const savedPerson: PersonWithTenant = await logFailure(entityService.save(person), 'Failed to save person for tenant01')

            expect(savedPerson).toBeDefined()
            expect(savedPerson.id).toBeDefined()

            const person2 = createTestPersonWithTenant(1, 'tenant02')
            const savedPerson2: PersonWithTenant = await logFailure(entityService.save(person2), 'Failed to save person for tenant02')

            expect(savedPerson2).toBeDefined()
            expect(savedPerson2.id).toBeDefined()

            await expect(entityService.syncIndex()).resolves.toBeNull()

            // Find the 1st person
            const foundPerson: PersonWithTenant = await logFailure(adminEntityService.findById({entityId: savedPerson.id as string, tenantId: 'tenant01'}), 'Failed to find person')
            expect(foundPerson).toBeDefined()
            expect(foundPerson.id).toBe(savedPerson.id)

            // Update the person
            // Find the 1st person
            const foundPerson2: PersonWithTenant = await logFailure(adminEntityService.findById({entityId: savedPerson2.id as string, tenantId: 'tenant02'}), 'Failed to find person')
            expect(foundPerson2).toBeDefined()
            expect(foundPerson2.id).toBe(savedPerson2.id)

            // Count the people
            await expect(adminEntityService.count(['tenant01', 'tenant02'])).resolves.toBe(2)
            await expect(adminEntityService.count(['tenant01'])).resolves.toBe(1)
            await expect(adminEntityService.count(['tenant02'])).resolves.toBe(1)

            // Delete the person
            await expect(adminEntityService.deleteById({entityId: savedPerson.id as string, tenantId: 'tenant01'})).resolves.toBeNull()
            await expect(adminEntityService.deleteById({entityId: savedPerson2.id as string, tenantId: 'tenant02'})).resolves.toBeNull()

            await expect(entityService.syncIndex()).resolves.toBeNull()

            // Count the people
            await expect(adminEntityService.count(['tenant01', 'tenant02'])).resolves.toBe(0)
        }
    )

    it<LocalTestContext>(
        'Test FindByIds ',
        async ({entityService, adminEntityService}) => {
            await createTestPeopleWithTenantAndVerify(adminEntityService, entityService, 'tenant01', 100)
            await createTestPeopleWithTenantAndVerify(adminEntityService, entityService, 'tenant02', 100)

            // make sure we get a count of all entries
            await expect(adminEntityService.count(['tenant01', 'tenant02'])).resolves.toBe(200)

            // Find all the people
            let elementsFound = 0
            let peopleIds: TenantSpecificId[] = []
            const pageable = Pageable.createWithCursor(null,
                                                       10,
                                                       { orders: [
                                                               new Order('firstName', Direction.ASC),
                                                               new Order('id', Direction.ASC)
                                                           ] })
            const pageOne = await adminEntityService.findAll(['tenant01'], pageable)
            for await(const page of pageOne){
                expect(page).toBeDefined()
                expect(page.content?.length).toBe(10)
                if(page.content) {
                    for (const person of page.content) {
                        elementsFound++
                        if (elementsFound % 2 === 0) {
                            peopleIds.push({entityId: person.id as string, tenantId: 'tenant01'})
                        }
                    }
                }else{
                    throw new Error('Page content is undefined')
                }
            }
            expect(elementsFound, 'Should have found 100 Entities').toBe(100)

            const peopleByIds = await adminEntityService.findByIds(peopleIds)
            for(const person of peopleByIds){
                expect(person).toBeDefined()
                expect(person.id).toBeDefined()
                expect(person.tenantId).toBe('tenant01')
            }
            expect(peopleByIds.length, 'Should have 50 Entities when using findByIds').toBe(50)

        }
    )

    it<LocalTestContext>(
        'Test FindByIds and None Found ',
        async ({entityService, adminEntityService}) => {
            await createTestPeopleWithTenantAndVerify(adminEntityService, entityService, 'tenant01', 50)
            await createTestPeopleWithTenantAndVerify(adminEntityService, entityService, 'tenant02', 50)

            // Find all the people
            let elementsFound = 0
            let peopleIds: TenantSpecificId[] = []
            const pageable = Pageable.createWithCursor(null,
                                                       10,
                                                       { orders: [
                                                               new Order('firstName', Direction.ASC),
                                                               new Order('id', Direction.ASC)
                                                           ] })
            const pageOne = await adminEntityService.findAll(['tenant01'], pageable)
            for await(const page of pageOne){
                expect(page).toBeDefined()
                expect(page.content?.length).toBe(10)

                if(page.content) {
                    for (const person of page.content) {
                        elementsFound++
                        if (elementsFound % 2 === 0) {
                            peopleIds.push({entityId: 'aaaaa' + person.id + 'aaaaa', tenantId: 'tenant01'})
                        }
                    }
                }else {
                    throw new Error('Page content is undefined')
                }
            }
            expect(elementsFound, 'Should have found 50 Entities').toBe(50)

            const peopleByIds = await adminEntityService.findByIds(peopleIds)
            expect(peopleByIds).toBeDefined()
            expect(peopleByIds.length, 'Should have 0 Entities (empty array) when using Admin findByIds and none match').toBe(0)

        }
    )

    it<LocalTestContext>(
        'Test CountByQuery',
        async ({entityService, adminEntityService}) => {
            await createTestPeopleWithTenantAndVerify(adminEntityService, entityService, 'tenant01', 100)
            await createTestPeopleWithTenantAndVerify(adminEntityService, entityService, 'tenant02', 100)

            expect(await adminEntityService.countByQuery("lastName: Doe", ['tenant01']),
                   'Should have 50 Entities when using Admin countByQuery tenant01').toBe(50)

            expect(await adminEntityService.countByQuery("lastName: Doe", ['tenant02']),
                   'Should have 50 Entities when using Admin countByQuery tenant02').toBe(50)

            expect(await adminEntityService.countByQuery("lastName: Doe", ['tenant01', 'tenant02']),
                   'Should have 100 Entities when using Admin countByQuery tenant01 and tenant02').toBe(100)

        }
    )

    it<LocalTestContext>(
        'Test CountByQuery and DeleteByQuery',
        async ({entityService, adminEntityService}) => {
            await createTestPeopleWithTenantAndVerify(adminEntityService, entityService, 'tenant01', 100)
            await createTestPeopleWithTenantAndVerify(adminEntityService, entityService, 'tenant02', 100)

            await adminEntityService.deleteByQuery("lastName: Doe", ['tenant01'])
            await entityService.syncIndex()

            let countByQueryAfterDelete = await adminEntityService.countByQuery("lastName: Doe", ['tenant01'])
            expect(countByQueryAfterDelete, 'Should have 0 tenant01 Entities when using countByQuery after deleteByQuery').toBe(0)

            expect(await adminEntityService.countByQuery("lastName: Doe", ['tenant02']),
                   'Should have 50 tenant02 Entities after delete by query for tenant01').toBe(50)

            await adminEntityService.deleteByQuery("lastName: Doe", ['tenant02'])
            await entityService.syncIndex()

            expect(await adminEntityService.countByQuery("lastName: Doe", ['tenant02']),
                   'Should have 0 tenant02 Entities when using countByQuery after deleteByQuery').toBe(0)
        }
    )

    it<LocalTestContext>(
        'Test Bulk CRUD',
        async ({entityService, adminEntityService}) => {
            // Create people
            await createTestPeopleWithTenantAndVerify(adminEntityService, entityService, 'tenant01', 100)
            await createTestPeopleWithTenantAndVerify(adminEntityService, entityService, 'tenant02', 100)
            await createTestPeopleWithTenantAndVerify(adminEntityService, entityService, 'tenant03', 100)

            // Find all the people ordered by name so we change only John Doe users
            const sort: Sort = new Sort()
            sort.orders = [new Order('lastName', Direction.ASC)]
            const page: Page<PersonWithTenant> = await adminEntityService.findAll(['tenant01'], Pageable.create(0, 10, sort))
            expect(page).toBeDefined()
            expect(page.totalElements).toBe(100)
            expect(page.content?.length).toBe(10)

            // Update the first 10 people
            if(page.content) {
                for (let person of page.content) {
                    person.firstName = 'Walter'
                    person.lastName = 'White'
                    // We do this to ensure the update performs a partial update properly
                    // @ts-ignore
                    delete person.address
                }
            }else {
                throw new Error('Page content is undefined')
            }

            await expect(entityService.bulkUpdate(page.content)).resolves.toBeNull()

            await expect(entityService.syncIndex()).resolves.toBeNull()

            // Search for all the people
            const searchPage: Page<PersonWithTenant> = await adminEntityService.search('firstName:Walter', ['tenant01', 'tenant02'], Pageable.create(0, 100))
            expect(searchPage).toBeDefined()
            expect(searchPage.totalElements).toBe(10)
            expect(searchPage.content?.length).toBe(10)

            // ensure all the people still have an address
            if(searchPage.content) {
                for (let person of searchPage.content) {
                    expect(person.address).toBeDefined()
                }
            }else{
                throw new Error('Search page content is undefined')
            }

            // now test to make sure we can find all the other people we created
            const searchPage2: Page<PersonWithTenant> = await adminEntityService.search('lastName:Doe', ['tenant01', 'tenant02'], Pageable.create(0, 25))
            expect(searchPage2).toBeDefined()
            expect(searchPage2.totalElements).toBe(90)
            expect(searchPage2.content?.length).toBe(25)

            const searchPage3: Page<PersonWithTenant> = await adminEntityService.search('lastName:Doe', ['tenant01'], Pageable.create(0, 25))
            expect(searchPage3).toBeDefined()
            expect(searchPage3.totalElements).toBe(40)
            expect(searchPage3.content?.length).toBe(25)

            const searchPage4: Page<PersonWithTenant> = await adminEntityService.search('lastName:Doe', ['tenant02'], Pageable.create(0, 25))
            expect(searchPage4).toBeDefined()
            expect(searchPage4.totalElements).toBe(50)
            expect(searchPage4.content?.length).toBe(25)
        }
    )

})
