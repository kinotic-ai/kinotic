import {Kinotic, KinoticSingleton, Page, Pageable} from '@kinotic-ai/core'
import {ArrayC3Type, FunctionDefinition, LongC3Type, ObjectC3Type, StringC3Type} from '@kinotic-ai/idl'
import {EntityDefinition, NamedQueriesDefinition, PageableC3Type, PageC3Type, QueryDecorator} from '@kinotic-ai/os-api'
import {EntitiesRepository, EntityRepository, IEntityRepository} from '@kinotic-ai/persistence'
import * as allure from 'allure-js-commons'
import {afterAll, afterEach, beforeAll, beforeEach, describe, expect, it} from 'vitest'
import {Person} from '../domain/Person.js'
import {
    createPersonEntityDefinitionIfNotExist,
    createTestPeopleAndVerify,
    deleteEntityDefinition,
    generateRandomString,
    initKinoticAppClient,
    initKinoticClient,
    shutdownKinoticClient,
} from '../TestHelpers.js'

const APP_TENANT = 'kinotic'

interface LocalTestContext {
    entityDefinition: EntityDefinition
    applicationIdUsed: string
    projectIdUsed: string
    appKinotic: KinoticSingleton
    entityService: IEntityRepository<Person>
}

describe('End To End Tests', () => {

    beforeAll(async () => {
        await allure.suite('Typescript Client')
        await allure.subSuite('Named Query Tests')
        await initKinoticClient()
    }, 300000)

    afterAll(async () => {
        await shutdownKinoticClient()
    }, 60000)

    beforeEach<LocalTestContext>(async (context) => {
        context.applicationIdUsed = generateRandomString(10)
        context.projectIdUsed = generateRandomString(5)
        context.entityDefinition = await createPersonEntityDefinitionIfNotExist(context.applicationIdUsed, context.projectIdUsed)
        expect(context.entityDefinition).toBeDefined()
        context.appKinotic = await initKinoticAppClient(context.entityDefinition.applicationId, APP_TENANT)
        context.entityService = new EntityRepository(
            context.entityDefinition.organizationId,
            context.entityDefinition.applicationId,
            context.entityDefinition.name,
            new EntitiesRepository(context.appKinotic)
        )
        expect(context.entityService).toBeDefined()
    })

    afterEach<LocalTestContext>(async (context) => {
        await context.appKinotic.disconnect()
        await expect(deleteEntityDefinition(context.entityDefinition.id as string)).resolves.toBeUndefined()
        await expect(Kinotic.entityDefinitions.syncIndex()).resolves.toBeNull()
        await Kinotic.projects.deleteById(context.entityDefinition.projectId)
        await expect(Kinotic.projects.syncIndex()).resolves.toBeNull()
        await Kinotic.applications.deleteById(context.entityDefinition.applicationId)
    })


    it<LocalTestContext>(
        'Aggregate Test',
        async ({entityService, applicationIdUsed, projectIdUsed}) => {
            // Create people
            await createTestPeopleAndVerify(entityService, 100)

            const structureId = entityService.entityId

            const query = new QueryDecorator(`SELECT COUNT(firstName) as count FROM "kinotic_${structureId}"`)
            const namedQuery = new FunctionDefinition('countAllPeople', [query])
            namedQuery.returnType = new ArrayC3Type(new ObjectC3Type('PeopleCount', applicationIdUsed)
                                                        .addProperty("count", new LongC3Type()))

            const namedQueriesDefinition = new NamedQueriesDefinition(structureId,
                                                                      applicationIdUsed,
                                                                      projectIdUsed,
                                                                      entityService.entityName,
                                                                      [namedQuery])


            const namedQueriesService = Kinotic.namedQueriesDefinitions
            await namedQueriesService.saveSync(namedQueriesDefinition)

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

            const structureId = entityService.entityId

            const query = new QueryDecorator(`SELECT COUNT(firstName) as count, lastName FROM "kinotic_${structureId}" WHERE lastName = ? GROUP BY lastName`)
            const namedQuery = new FunctionDefinition('countPeopleByLastNameWithLastName', [query])
            namedQuery.addParameter('lastName', new StringC3Type())
            const contentType = new ObjectC3Type('CountByLastName', applicationIdUsed)
                .addProperty("count", new LongC3Type())
                .addProperty("lastName", new StringC3Type())
            namedQuery.returnType = new ArrayC3Type(contentType)

            const namedQueriesDefinition = new NamedQueriesDefinition(structureId,
                                                                      applicationIdUsed,
                                                                      projectIdUsed,
                                                                      entityService.entityName,
                                                                      [namedQuery])


            const namedQueriesService = Kinotic.namedQueriesDefinitions
            await namedQueriesService.saveSync(namedQueriesDefinition)

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

            const structureId = entityService.entityId
            const query = new QueryDecorator(`SELECT COUNT(firstName) as count, lastName FROM "kinotic_${structureId}" GROUP BY lastName`)
            const namedQuery = new FunctionDefinition('countPeopleByLastNamePage', [query])
            namedQuery.addParameter('pageable', new PageableC3Type())
            const contentType = new ObjectC3Type('CountByLastName', applicationIdUsed)
                .addProperty("count", new LongC3Type())
                .addProperty("lastName", new StringC3Type())
            namedQuery.returnType = new PageC3Type(contentType)

            const namedQueriesDefinition = new NamedQueriesDefinition(structureId,
                                                                      applicationIdUsed,
                                                                      projectIdUsed,
                                                                      entityService.entityName,
                                                                      [namedQuery])


            const namedQueriesService = Kinotic.namedQueriesDefinitions
            await namedQueriesService.saveSync(namedQueriesDefinition)

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
            const structureId = entityService.entityId
            const namedQueriesService = Kinotic.namedQueriesDefinitions

            const query = new QueryDecorator(`SELECT COUNT(firstName) as count FROM "kinotic_${structureId}"`)
            const namedQuery = new FunctionDefinition('countAllPeople', [query])
            namedQuery.returnType = new ArrayC3Type(new ObjectC3Type('PeopleCount', applicationIdUsed)
                                                        .addProperty("count", new LongC3Type()))


            const query2 = new QueryDecorator(`SELECT COUNT(firstName) as count, lastName FROM "kinotic_${structureId}" WHERE lastName = ? GROUP BY lastName`)
            const namedQuery2 = new FunctionDefinition('countPeopleByLastNameWithLastName', [query2])
            namedQuery2.addParameter('lastName', new StringC3Type())
            const contentType2 = new ObjectC3Type('CountByLastName', applicationIdUsed)
                .addProperty("count", new LongC3Type())
                .addProperty("lastName", new StringC3Type())
            namedQuery2.returnType = new ArrayC3Type(contentType2)


            const query3 = new QueryDecorator(`SELECT COUNT(firstName) as count, lastName FROM "kinotic_${structureId}" GROUP BY lastName`)
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
                                                                      entityService.entityName,
                                                                      [namedQuery, namedQuery2, namedQuery3])
            await namedQueriesService.saveSync(namedQueriesDefinition)
        }
    )


})
