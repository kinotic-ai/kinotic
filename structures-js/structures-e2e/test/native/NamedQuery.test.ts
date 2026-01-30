import {Page, Pageable} from '@kinotic/continuum-client'
import {ArrayC3Type, FunctionDefinition, LongC3Type, ObjectC3Type, StringC3Type} from '@kinotic/continuum-idl'
import {
    IEntityService,
    NamedQueriesDefinition,
    PageableC3Type,
    PageC3Type,
    QueryDecorator,
    Structure,
    Structures
} from '@kinotic/structures-api'
import * as allure from 'allure-js-commons'
import {afterAll, afterEach, beforeAll, beforeEach, describe, expect, it} from 'vitest'
import {WebSocket} from 'ws'
import {Person} from '../domain/Person.js'
import {
    createPersonStructureIfNotExist,
    createTestPeopleAndVerify,
    deleteStructure,
    generateRandomString,
    initContinuumClient,
    shutdownContinuumClient,
} from '../TestHelpers.js'

Object.assign(global, { WebSocket})

interface LocalTestContext {
    structure: Structure
    applicationIdUsed: string
    projectIdUsed: string
    entityService: IEntityService<Person>
}

describe('End To End Tests', () => {

    beforeAll(async () => {
        await allure.suite('Typescript Client')
        await allure.subSuite('Named Query Tests')
        await initContinuumClient()
    }, 300000)

    afterAll(async () => {
        await shutdownContinuumClient()
    }, 60000)

    beforeEach<LocalTestContext>(async (context) => {
        context.applicationIdUsed = generateRandomString(10)
        context.projectIdUsed = generateRandomString(5)
        context.structure = await createPersonStructureIfNotExist(context.applicationIdUsed, context.projectIdUsed)
        expect(context.structure).toBeDefined()
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
        'Aggregate Test',
        async ({entityService, applicationIdUsed, projectIdUsed}) => {
            // Create people
            await createTestPeopleAndVerify(entityService, 100)

            const structureId = entityService.structureId

            const query = new QueryDecorator(`SELECT COUNT(firstName) as count FROM "struct_${structureId}"`)
            const namedQuery = new FunctionDefinition('countAllPeople', [query])
            namedQuery.returnType = new ArrayC3Type(new ObjectC3Type('PeopleCount', applicationIdUsed)
                                                        .addProperty("count", new LongC3Type()))

            const namedQueriesDefinition = new NamedQueriesDefinition(structureId,
                                                                      applicationIdUsed,
                                                                      projectIdUsed,
                                                                      entityService.structureName,
                                                                      [namedQuery])


            const namedQueriesService = Structures.getNamedQueriesService()
            await namedQueriesService.save(namedQueriesDefinition)

            const countResult: any = await entityService.namedQuery('countAllPeople', [])
            expect(countResult).toBeDefined()
            expect(countResult).toHaveLength(1)
            expect(countResult[0]).toBeDefined()
            expect(countResult[0].count).toBe(100)
        }
    )

    it<LocalTestContext>(
        'Aggregate With Parameter Test',
        async ({entityService, applicationIdUsed, projectIdUsed}) => {
            // Create people
            await createTestPeopleAndVerify(entityService, 100)

            const structureId = entityService.structureId

            const query = new QueryDecorator(`SELECT COUNT(firstName) as count, lastName FROM "struct_${structureId}" WHERE lastName = ? GROUP BY lastName`)
            const namedQuery = new FunctionDefinition('countPeopleByLastNameWithLastName', [query])
            namedQuery.addParameter('lastName', new StringC3Type())
            const contentType = new ObjectC3Type('CountByLastName', applicationIdUsed)
                .addProperty("count", new LongC3Type())
                .addProperty("lastName", new StringC3Type())
            namedQuery.returnType = new ArrayC3Type(contentType)

            const namedQueriesDefinition = new NamedQueriesDefinition(structureId,
                                                                      applicationIdUsed,
                                                                      projectIdUsed,
                                                                      entityService.structureName,
                                                                      [namedQuery])


            const namedQueriesService = Structures.getNamedQueriesService()
            await namedQueriesService.save(namedQueriesDefinition)

            const countResult: any = await entityService.namedQuery('countPeopleByLastNameWithLastName',
                                                                    [{key: 'lastName', value: 'Doe'}])

            expect(countResult).toBeDefined()
            expect(countResult).toHaveLength(1)
            expect(countResult[0]).toBeDefined()
            expect(countResult[0].count).toBe(50)
        }
    )

    it<LocalTestContext>(
        'Aggregate Pageable Test',
        async ({entityService, applicationIdUsed, projectIdUsed}) => {
            // Create people
            await createTestPeopleAndVerify(entityService, 100)

            const structureId = entityService.structureId
            const query = new QueryDecorator(`SELECT COUNT(firstName) as count, lastName FROM "struct_${structureId}" GROUP BY lastName`)
            const namedQuery = new FunctionDefinition('countPeopleByLastNamePage', [query])
            namedQuery.addParameter('pageable', new PageableC3Type())
            const contentType = new ObjectC3Type('CountByLastName', applicationIdUsed)
                .addProperty("count", new LongC3Type())
                .addProperty("lastName", new StringC3Type())
            namedQuery.returnType = new PageC3Type(contentType)

            const namedQueriesDefinition = new NamedQueriesDefinition(structureId,
                                                                      applicationIdUsed,
                                                                      projectIdUsed,
                                                                      entityService.structureName,
                                                                      [namedQuery])


            const namedQueriesService = Structures.getNamedQueriesService()
            await namedQueriesService.save(namedQueriesDefinition)

            const pageable = Pageable.createWithCursor(null, 1)
            const personPage: Page<Person> = await entityService.namedQueryPage('countPeopleByLastNamePage',
                                                                                [],
                                                                                pageable)
            expect(personPage.cursor).toBeDefined()
            expect(personPage.content).toHaveLength(1)

            const personPage2: Page<Person> = await entityService.namedQueryPage('countPeopleByLastNamePage',
                                                                                 [],
                                                                                 Pageable.createWithCursor(personPage.cursor as string, 1))
            expect(personPage2.cursor).toBeDefined()
            expect(personPage2.content).toHaveLength(1)

            const personPage3: Page<Person> = await entityService.namedQueryPage('countPeopleByLastNamePage',
                                                                                 [],
                                                                                 Pageable.createWithCursor(personPage2.cursor as string, 1))
            expect(personPage3.cursor).toBeNull()
            expect(personPage3.content).toHaveLength(0)
        }
    )

    it<LocalTestContext>(
        'Test Save Multiple',
        async ({entityService, applicationIdUsed, projectIdUsed}) => {
            const structureId = entityService.structureId
            const namedQueriesService = Structures.getNamedQueriesService()

            const query = new QueryDecorator(`SELECT COUNT(firstName) as count FROM "struct_${structureId}"`)
            const namedQuery = new FunctionDefinition('countAllPeople', [query])
            namedQuery.returnType = new ArrayC3Type(new ObjectC3Type('PeopleCount', applicationIdUsed)
                                                        .addProperty("count", new LongC3Type()))


            const query2 = new QueryDecorator(`SELECT COUNT(firstName) as count, lastName FROM "struct_${structureId}" WHERE lastName = ? GROUP BY lastName`)
            const namedQuery2 = new FunctionDefinition('countPeopleByLastNameWithLastName', [query2])
            namedQuery2.addParameter('lastName', new StringC3Type())
            const contentType2 = new ObjectC3Type('CountByLastName', applicationIdUsed)
                .addProperty("count", new LongC3Type())
                .addProperty("lastName", new StringC3Type())
            namedQuery2.returnType = new ArrayC3Type(contentType2)


            const query3 = new QueryDecorator(`SELECT COUNT(firstName) as count, lastName FROM "struct_${structureId}" GROUP BY lastName`)
            const namedQuery3 = new FunctionDefinition('countPeopleByLastNamePage', [query3])
            namedQuery3.addParameter('pageable', new PageableC3Type())
            const contentType3 = new ObjectC3Type('CountByLastName', applicationIdUsed)
                .addProperty("count", new LongC3Type())
                .addProperty("lastName", new StringC3Type())
            namedQuery3.returnType = new PageC3Type(contentType3)

            // Save the named queries
            const namedQueriesDefinition = new NamedQueriesDefinition(structureId,
                                                                      applicationIdUsed,
                                                                      projectIdUsed,
                                                                      entityService.structureName,
                                                                      [namedQuery, namedQuery2, namedQuery3])
            await namedQueriesService.save(namedQueriesDefinition)
        }
    )


})
