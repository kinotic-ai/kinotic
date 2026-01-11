import {Direction, Order, Page, Pageable} from '@mindignited/continuum-client'
import {IEntityService, Structure, Structures} from '@mindignited/structures-api'
import * as allure from 'allure-js-commons'
import {afterAll, afterEach, beforeAll, beforeEach, describe, expect, it} from 'vitest'
import {WebSocket} from 'ws'
import {Person} from '../domain/Person.js'
import {
    createPersonStructureIfNotExist,
    createTestPeopleAndVerify,
    createTestPerson,
    deleteStructure,
    findAndVerifyPeopleWithCursorPaging,
    findAndVerifyPeopleWithOffsetPaging,
    generateRandomString,
    initContinuumClient,
    logFailure,
    shutdownContinuumClient
} from '../TestHelpers.js'

Object.assign(global, { WebSocket})

interface LocalTestContext {
    structure: Structure
    entityService: IEntityService<Person>
}

describe('End To End Tests', () => {

    beforeAll(async () => {
        await allure.suite('Typescript Client')
        await allure.subSuite('EntityService Tests')
        await initContinuumClient()
    }, 300000)

    afterAll(async () => {
        await shutdownContinuumClient()
    }, 60000)

    beforeEach<LocalTestContext>(async (context) => {
        context.structure = await createPersonStructureIfNotExist(generateRandomString(10), generateRandomString(5))
        expect(context.structure).toBeDefined()
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
        'Test Basic CRUD',
        async ({entityService}) => {
            // Create a person
            const person = createTestPerson()
            const savedPerson: Person = await logFailure(entityService.save(person), 'Failed to save person')

            expect(savedPerson).toBeDefined()
            expect(savedPerson.id).toBeDefined()

            // Find the person
            const foundPerson: Person = await logFailure(entityService.findById(savedPerson.id as string), 'Failed to find person')
            expect(foundPerson).toBeDefined()
            expect(foundPerson.id).toBe(savedPerson.id)

            // Update the person
            foundPerson.firstName = 'Walter'
            foundPerson.lastName = 'White'
            const updatedPerson: Person = await logFailure(entityService.update(foundPerson), 'Failed to update person')
            expect(updatedPerson).toBeDefined()
            expect(updatedPerson.id).toBe(foundPerson.id)

            // Find the updated person
            const foundUpdatedPerson: Person = await logFailure(entityService.findById(updatedPerson.id as string), 'Failed to find updated person')
            expect(foundUpdatedPerson).toBeDefined()
            expect(foundUpdatedPerson.id).toBe(updatedPerson.id)
            expect(foundUpdatedPerson.firstName).toBe('Walter')
            expect(foundUpdatedPerson.lastName).toBe('White')

            // Count the people
            await expect(entityService.count()).resolves.toBe(1)

            // Delete the person
            await expect(entityService.deleteById(savedPerson.id as string)).resolves.toBeNull()
        }
    )

    it<LocalTestContext>(
        'Test FindByIds ',
        async ({entityService}) => {
            await createTestPeopleAndVerify(entityService, 100)

            // Find all the people
            let elementsFound = 0
            let peopleIds: string[] = []
            const pageable = Pageable.createWithCursor(null,
                10,
                { orders: [
                        new Order('firstName', Direction.ASC),
                        new Order('id', Direction.ASC)
                    ] })
            const pageOne = await entityService.findAll(pageable)
            for await(const page of pageOne){
                expect(page).toBeDefined()
                expect(page.content?.length).toBe(10)
                if(page.content) {
                    for (const person of page.content) {
                        elementsFound++
                        if (elementsFound % 2 === 0) {
                            peopleIds.push(person.id as string)
                        }
                    }
                }else{
                    throw new Error('Page content is null')
                }
            }
            expect(elementsFound, 'Should have found 100 Entities').toBe(100)

            const peopleByIds = await entityService.findByIds(peopleIds)
            expect(peopleByIds.length, 'Should have 50 Entities when using findByIds').toBe(50)

        }
    )

    it<LocalTestContext>(
        'Test FindByIds and None Found ',
        async ({entityService}) => {
            await createTestPeopleAndVerify(entityService, 50)

            // Find all the people
            let elementsFound = 0
            let peopleIds: string[] = []
            const pageable = Pageable.createWithCursor(null,
                10,
                { orders: [
                        new Order('firstName', Direction.ASC),
                        new Order('id', Direction.ASC)
                    ] })
            const pageOne = await entityService.findAll(pageable)
            for await(const page of pageOne){
                expect(page).toBeDefined()
                expect(page.content?.length).toBe(10)
                if(page.content) {
                    for (const person of page.content) {
                        elementsFound++
                        if (elementsFound % 2 === 0) {
                            peopleIds.push('aaaaa' + person.id + 'aaaaa')
                        }
                    }
                }else{
                    throw new Error('Page content is null')
                }
            }
            expect(elementsFound, 'Should have found 50 Entities').toBe(50)

            const peopleByIds = await entityService.findByIds(peopleIds)
            expect(peopleByIds).toBeDefined()
            expect(peopleByIds.length, 'Should have 0 Entities (empty array) when using findByIds and none match').toBe(0)

        }
    )

    it<LocalTestContext>(
        'Test CountByQuery',
        async ({entityService}) => {
            await createTestPeopleAndVerify(entityService, 100)

            let countByQuery = await entityService.countByQuery("lastName: Doe")
            expect(countByQuery, 'Should have 50 Entities when using countByQuery').toBe(50)

        }
    )

    it<LocalTestContext>(
        'Test CountByQuery and DeleteByQuery',
        async ({entityService}) => {
            await createTestPeopleAndVerify(entityService, 100)

            await entityService.deleteByQuery("lastName: Doe")
            await entityService.syncIndex()

            let countByQueryAfterDelete = await entityService.countByQuery("lastName: Doe")
            expect(countByQueryAfterDelete, 'Should have 0 Entities when using countByQuery after deleteByQuery').toBe(0)

        }
    )

    it<LocalTestContext>(
        'Test Cursor Based Paging',
         async ({entityService}) => {
             await createTestPeopleAndVerify(entityService, 100)

             // Find all the people
             let cursor: string | null = null
             let done = false
             let elementsFound = 0
             while(!done) {
                 const pageable = Pageable.createWithCursor(cursor,
                                                            10,
                                                            { orders: [
                                                                new Order('firstName', Direction.ASC),
                                                                new Order('id', Direction.ASC)
                                                                ] })
                 const page: Page<Person> = await entityService.findAll(pageable)
                 expect(page).toBeDefined()
                 if(page.cursor) {
                     cursor = page.cursor
                     expect(page.content?.length).toBe(10)
                     elementsFound += page.content?.length || 0
                 }else{
                     done = true
                 }
             }
             expect(elementsFound, 'Should have found 100 Entities').toBe(100)
         }
    )

    it<LocalTestContext>(
        'Test Cursor Based Paging With Iterator',
         async ({entityService}) => {
             // Create people
             await createTestPeopleAndVerify(entityService, 100)

             // Find all the people
             await findAndVerifyPeopleWithCursorPaging(entityService, 100)
         }
    )

    it<LocalTestContext>(
        'Test Cursor Based Paging With Iterator Uneven Pages',
         async ({entityService}) => {
             // Create people
             await createTestPeopleAndVerify(entityService, 29)

             // Find all the people
             await findAndVerifyPeopleWithCursorPaging(entityService, 29)
         }
    )

    it<LocalTestContext>(
        'Test Cursor Based Paging With Iterator Single Page',
         async ({entityService}) => {
             // Create people
             await createTestPeopleAndVerify(entityService, 9)

             // Find all the people
             await findAndVerifyPeopleWithCursorPaging(entityService, 9)
         }
    )

    it<LocalTestContext>(
        'Test Cursor Based Paging With Iterator No Data',
         async ({entityService}) => {
             // Find all the people
             await findAndVerifyPeopleWithCursorPaging(entityService, 0)
         }
    )

    it<LocalTestContext>(
        'Test Offset Based Paging With Iterator',
         async ({entityService}) => {
             // Create people
             await createTestPeopleAndVerify(entityService, 100)

             // Find all the people
             await findAndVerifyPeopleWithOffsetPaging(entityService, 100)
         }
    )

    it<LocalTestContext>(
        'Test Offset Based Paging With Iterator Uneven Pages',
         async ({entityService}) => {
             // Create people
             await createTestPeopleAndVerify(entityService, 29)

             // Find all the people
             await findAndVerifyPeopleWithOffsetPaging(entityService, 29)
         }
    )

    it<LocalTestContext>(
        'Test Offset Based Paging With Iterator Single Page',
         async ({entityService}) => {
             // Create people
             await createTestPeopleAndVerify(entityService, 9)

             // Find all the people
             await findAndVerifyPeopleWithOffsetPaging(entityService, 9)
         }
    )

    it<LocalTestContext>(
        'Test Offset Based Paging With Iterator No Data',
         async ({entityService}) => {
             // Find all the people
             await findAndVerifyPeopleWithOffsetPaging(entityService, 0)
         }
    )

    it<LocalTestContext>(
        'Test Bulk CRUD',
        async ({entityService}) => {
            // Create people
            await createTestPeopleAndVerify(entityService, 100)

            // Find all the people
            const page: Page<Person> = await entityService.findAll(Pageable.create(0, 10))
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
            }else{
                throw new Error('Page content is null')
            }


            await expect(entityService.bulkUpdate(page.content)).resolves.toBeNull()

            await expect(entityService.syncIndex()).resolves.toBeNull()

            // Search for all the people
            const searchPage: Page<Person> = await entityService.search('firstName:Walter',Pageable.create(0, 10))
            expect(searchPage).toBeDefined()
            expect(searchPage.totalElements).toBe(10)
            expect(searchPage.content?.length).toBe(10)

            // ensure all the people still have an address
            if(searchPage.content) {
                for (let person of searchPage.content) {
                    expect(person.address).toBeDefined()
                }
            }else {
                throw new Error('Search page content is null')
            }
        }
    )

})
