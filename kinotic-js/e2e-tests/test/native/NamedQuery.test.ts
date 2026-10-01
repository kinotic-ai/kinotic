import {Kinotic, KinoticSingleton, Page, Pageable} from '@kinotic-ai/core'
import {ArrayC3Type, FunctionDefinition, LongC3Type, ObjectC3Type, StringC3Type} from '@kinotic-ai/idl'
import {EntityDefinition, NamedQueriesDefinition, PageableC3Type, PageC3Type, QueryDecorator} from '@kinotic-ai/management-api'
import {EntitiesRepository, EntityRepository, IEntityRepository} from '@kinotic-ai/persistence'
import * as allure from 'allure-js-commons'
import {afterAll, afterEach, beforeAll, beforeEach, describe, expect, it} from 'vitest'
import {Person} from '../domain/Person.js'
import {
    E2E_APP_TENANT as APP_TENANT,
    E2E_ORGANIZATION_ID as TEST_ORG_ID,
    createPersonEntityDefinitionIfNotExist,
    createTestPeopleAndVerify,
    deleteEntityDefinition,
    generateRandomString,
    initKinoticAppClient,
    initKinoticClient,
    shutdownKinoticClient,
} from '../TestHelpers.js'

// The app user for this (APP_ID, APP_TENANT) pair is seeded by the V3__e2e_app_fixtures migration.
const APP_ID = 'e2e-named-query'

interface LocalTestContext {
    entityDefinition: EntityDefinition
    applicationIdUsed: string
    projectIdUsed: string
    appKinotic: KinoticSingleton
    entityService: IEntityRepository<Person>
}

/** The row every write returns: the number of documents it wrote. */
function writeCount(applicationId: string): ArrayC3Type {
    return new ArrayC3Type(new ObjectC3Type('WriteCount', applicationId).addProperty('count', new LongC3Type()))
}

describe('Kinotic JS', () => {

    beforeAll(async () => {
        await allure.suite('e2e-tests/native')
        await allure.subSuite('NamedQuery')
        await initKinoticClient()
    }, 300000)

    afterAll(async () => {
        await shutdownKinoticClient()
    }, 60000)

    beforeEach<LocalTestContext>(async (context) => {
        context.applicationIdUsed = APP_ID
        context.projectIdUsed = generateRandomString(5)
        context.entityDefinition = await createPersonEntityDefinitionIfNotExist(TEST_ORG_ID, context.applicationIdUsed, context.projectIdUsed)
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
    })


    it<LocalTestContext>(
        'Aggregate Test',
        async ({entityService, applicationIdUsed, projectIdUsed}) => {
            // Create people
            await createTestPeopleAndVerify(entityService, 100)

            const structureId = entityService.entityId

            const query = new QueryDecorator('SELECT COUNT(firstName) AS count FROM Person')
            const namedQuery = new FunctionDefinition('countAllPeople', [query])
            namedQuery.returnType = new ArrayC3Type(new ObjectC3Type('PeopleCount', applicationIdUsed)
                                                        .addProperty("count", new LongC3Type()))

            const namedQueriesDefinition = new NamedQueriesDefinition(structureId,
                                                                      TEST_ORG_ID,
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
        'Runs the named query the management server saved last',
        async ({entityService, applicationIdUsed, projectIdUsed}) => {
            await createTestPeopleAndVerify(entityService, 100)

            const structureId = entityService.entityId
            const namedQueriesService = Kinotic.namedQueriesDefinitions

            // saved through the management server, run on the app server, which caches the query it runs
            const countAll = new FunctionDefinition('countPeople',
                                                    [new QueryDecorator('SELECT COUNT(firstName) AS count FROM Person')])
            countAll.returnType = new ArrayC3Type(new ObjectC3Type('PeopleCount', applicationIdUsed)
                                                      .addProperty('count', new LongC3Type()))
            await namedQueriesService.saveSync(new NamedQueriesDefinition(structureId,
                                                                          TEST_ORG_ID,
                                                                          applicationIdUsed,
                                                                          projectIdUsed,
                                                                          entityService.entityName,
                                                                          [countAll]))
            const before: any = await entityService.namedQuery('countPeople', [])
            expect(before).toHaveLength(1)
            expect(before[0].count).toBe(100)

            // saving the query again evicts the app server's cached one
            const countByLastName = new FunctionDefinition('countPeople',
                                                           [new QueryDecorator('SELECT COUNT(firstName) AS count, lastName FROM Person GROUP BY lastName')])
            countByLastName.returnType = new ArrayC3Type(new ObjectC3Type('PeopleCountByLastName', applicationIdUsed)
                                                             .addProperty('count', new LongC3Type())
                                                             .addProperty('lastName', new StringC3Type()))
            await namedQueriesService.saveSync(new NamedQueriesDefinition(structureId,
                                                                          TEST_ORG_ID,
                                                                          applicationIdUsed,
                                                                          projectIdUsed,
                                                                          entityService.entityName,
                                                                          [countByLastName]))
            const after: any = await entityService.namedQuery('countPeople', [])
            expect(after).toHaveLength(2)
            expect(after.map((row: any) => row.count)).toEqual([50, 50])
        }
    )

    it<LocalTestContext>(
        'Aggregate With Parameter Test',
        async ({entityService, applicationIdUsed, projectIdUsed}) => {
            // Create people
            await createTestPeopleAndVerify(entityService, 100)

            const structureId = entityService.entityId

            const query = new QueryDecorator('SELECT COUNT(firstName) AS count, lastName FROM Person WHERE lastName = :lastName GROUP BY lastName')
            const namedQuery = new FunctionDefinition('countPeopleByLastNameWithLastName', [query])
            namedQuery.addParameter('lastName', new StringC3Type())
            const contentType = new ObjectC3Type('CountByLastName', applicationIdUsed)
                .addProperty("count", new LongC3Type())
                .addProperty("lastName", new StringC3Type())
            namedQuery.returnType = new ArrayC3Type(contentType)

            const namedQueriesDefinition = new NamedQueriesDefinition(structureId,
                                                                      TEST_ORG_ID,
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
            const query = new QueryDecorator('SELECT COUNT(firstName) AS count, lastName FROM Person GROUP BY lastName')
            const namedQuery = new FunctionDefinition('countPeopleByLastNamePage', [query])
            namedQuery.addParameter('pageable', new PageableC3Type())
            const contentType = new ObjectC3Type('CountByLastName', applicationIdUsed)
                .addProperty("count", new LongC3Type())
                .addProperty("lastName", new StringC3Type())
            namedQuery.returnType = new PageC3Type(contentType)

            const namedQueriesDefinition = new NamedQueriesDefinition(structureId,
                                                                      TEST_ORG_ID,
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
        'Select Test',
        async ({entityService, entityDefinition, applicationIdUsed, projectIdUsed}) => {
            await createTestPeopleAndVerify(entityService, 100)

            const findByLastName = new FunctionDefinition('findByLastName',
                                                          [new QueryDecorator('SELECT * FROM Person WHERE lastName = :lastName ORDER BY firstName LIMIT 10')])
            findByLastName.addParameter('lastName', new StringC3Type())
            findByLastName.returnType = new ArrayC3Type(entityDefinition.schema)

            // a projection returns the listed fields only, a nested object whole
            const namesByLastName = new FunctionDefinition('namesByLastName',
                                                           [new QueryDecorator('SELECT firstName, address FROM Person WHERE lastName = :lastName')])
            namesByLastName.addParameter('lastName', new StringC3Type())
            namesByLastName.returnType = new ArrayC3Type(entityDefinition.schema)

            await Kinotic.namedQueriesDefinitions.saveSync(new NamedQueriesDefinition(entityService.entityId,
                                                                                      TEST_ORG_ID,
                                                                                      applicationIdUsed,
                                                                                      projectIdUsed,
                                                                                      entityService.entityName,
                                                                                      [findByLastName, namesByLastName]))

            const people: Person[] = await entityService.namedQuery('findByLastName', [{key: 'lastName', value: 'Doe'}])
            expect(people).toHaveLength(10)
            for (const person of people) {
                expect(person.lastName).toBe('Doe')
                expect(person.firstName).toBe('John')
                expect(person.age).toBe(42)
                expect(person.address).toEqual({street: '123 Main St', city: 'Anytown', state: 'CA', zip: '12345'})
                expect(person.myPet?.name).toBe('Fluffy')
            }

            const names: any[] = await entityService.namedQuery('namesByLastName', [{key: 'lastName', value: 'Wozniak'}])
            expect(names).toHaveLength(50)
            for (const row of names) {
                expect(row.firstName).toBe('Steve')
                expect(row.address.city).toBe('Anytown')
                expect(row.lastName).toBeUndefined()
                expect(row.myPet).toBeUndefined()
            }
        }
    )

    it<LocalTestContext>(
        'Select Pageable Test',
        async ({entityService, entityDefinition, applicationIdUsed, projectIdUsed}) => {
            await createTestPeopleAndVerify(entityService, 100)

            // the statement's ORDER BY is the sort a cursor pages by, so it has to order the rows fully
            const findByLastNamePage = new FunctionDefinition('findByLastNamePage',
                                                              [new QueryDecorator('SELECT * FROM Person WHERE lastName = :lastName ORDER BY firstName, id')])
            findByLastNamePage.addParameter('lastName', new StringC3Type())
            findByLastNamePage.addParameter('pageable', new PageableC3Type())
            findByLastNamePage.returnType = new PageC3Type(entityDefinition.schema)

            await Kinotic.namedQueriesDefinitions.saveSync(new NamedQueriesDefinition(entityService.entityId,
                                                                                      TEST_ORG_ID,
                                                                                      applicationIdUsed,
                                                                                      projectIdUsed,
                                                                                      entityService.entityName,
                                                                                      [findByLastNamePage]))

            const parameters = [{key: 'lastName', value: 'Doe'}]
            const page1: Page<Person> = await entityService.namedQueryPage('findByLastNamePage', parameters, Pageable.createWithCursor(null, 30))
            expect(page1.content).toHaveLength(30)
            expect(page1.cursor).toBeDefined()

            const page2: Page<Person> = await entityService.namedQueryPage('findByLastNamePage', parameters, Pageable.createWithCursor(page1.cursor as string, 30))
            expect(page2.content).toHaveLength(20)
            const ids = new Set([...(page1.content as Person[]), ...(page2.content as Person[])].map(person => person.id))
            expect(ids.size).toBe(50)

            const page3: Page<Person> = await entityService.namedQueryPage('findByLastNamePage', parameters, Pageable.createWithCursor(page2.cursor as string, 30))
            expect(page3.content).toHaveLength(0)

            const offsetPage: Page<Person> = await entityService.namedQueryPage('findByLastNamePage', parameters, Pageable.create(1, 20))
            expect(offsetPage.content).toHaveLength(20)
            expect(offsetPage.totalElements).toBe(50)
        }
    )

    it<LocalTestContext>(
        'Insert Update Delete Test',
        async ({entityService, applicationIdUsed, projectIdUsed}) => {
            const addPerson = new FunctionDefinition('addPerson',
                                                     [new QueryDecorator('INSERT INTO Person (id, firstName, lastName, age) VALUES (:id, :firstName, :lastName, :age) WITH REFRESH')])
            addPerson.addParameter('id', new StringC3Type())
            addPerson.addParameter('firstName', new StringC3Type())
            addPerson.addParameter('lastName', new StringC3Type())
            addPerson.addParameter('age', new LongC3Type())
            addPerson.returnType = writeCount(applicationIdUsed)

            const renameById = new FunctionDefinition('renameById',
                                                      [new QueryDecorator('UPDATE Person SET lastName = :lastName WHERE id = :id WITH REFRESH')])
            renameById.addParameter('lastName', new StringC3Type())
            renameById.addParameter('id', new StringC3Type())
            renameById.returnType = writeCount(applicationIdUsed)

            const deleteByLastName = new FunctionDefinition('deleteByLastName',
                                                            [new QueryDecorator('DELETE FROM Person WHERE lastName = :lastName WITH REFRESH')])
            deleteByLastName.addParameter('lastName', new StringC3Type())
            deleteByLastName.returnType = writeCount(applicationIdUsed)

            await Kinotic.namedQueriesDefinitions.saveSync(new NamedQueriesDefinition(entityService.entityId,
                                                                                      TEST_ORG_ID,
                                                                                      applicationIdUsed,
                                                                                      projectIdUsed,
                                                                                      entityService.entityName,
                                                                                      [addPerson, renameById, deleteByLastName]))

            const added: any = await entityService.namedQuery('addPerson', [
                {key: 'id', value: 'p-1'},
                {key: 'firstName', value: 'Grace'},
                {key: 'lastName', value: 'Hopper'},
                {key: 'age', value: 85}
            ])
            expect(added).toEqual([{count: 1}])
            // the row is stored the way the repository stores one, so it is found by id
            const grace = await entityService.findById('p-1')
            expect(grace?.firstName).toBe('Grace')
            expect(grace?.lastName).toBe('Hopper')
            expect(grace?.age).toBe(85)
            await expect(entityService.count()).resolves.toBe(1)

            const renamed: any = await entityService.namedQuery('renameById', [{key: 'lastName', value: 'Hopper-Murray'}, {key: 'id', value: 'p-1'}])
            expect(renamed).toEqual([{count: 1}])
            expect((await entityService.findById('p-1'))?.lastName).toBe('Hopper-Murray')

            const deleted: any = await entityService.namedQuery('deleteByLastName', [{key: 'lastName', value: 'Hopper-Murray'}])
            expect(deleted).toEqual([{count: 1}])
            await expect(entityService.count()).resolves.toBe(0)
        }
    )

    it<LocalTestContext>(
        'rejects a query that names another entity, or is not a query',
        async ({entityService, entityDefinition, applicationIdUsed, projectIdUsed}) => {
            const otherEntity = new FunctionDefinition('otherEntity', [new QueryDecorator('SELECT * FROM Vehicle')])
            otherEntity.returnType = new ArrayC3Type(entityDefinition.schema)
            const otherEntityAggregate = new FunctionDefinition('otherEntityAggregate', [new QueryDecorator('SELECT COUNT(firstName) AS count FROM Vehicle')])
            otherEntityAggregate.returnType = writeCount(applicationIdUsed)
            // FROM names one entity; an index pattern, or a decoy FROM inside a string, does not parse
            const hiddenIndexAggregate = new FunctionDefinition('hiddenIndexAggregate', [new QueryDecorator(`SELECT COUNT(firstName) AS count FROM "kinotic_*" WHERE 'x' <> 'FROM Person'`)])
            hiddenIndexAggregate.returnType = writeCount(applicationIdUsed)
            const commentedAggregate = new FunctionDefinition('commentedAggregate', [new QueryDecorator('SELECT COUNT(firstName) AS count FROM Person /* x */')])
            commentedAggregate.returnType = writeCount(applicationIdUsed)
            const reindex = new FunctionDefinition('reindex', [new QueryDecorator('REINDEX Person INTO Person')])
            reindex.returnType = writeCount(applicationIdUsed)
            const createTable = new FunctionDefinition('createTable', [new QueryDecorator('CREATE TABLE Person (id KEYWORD)')])
            createTable.returnType = writeCount(applicationIdUsed)

            await Kinotic.namedQueriesDefinitions.saveSync(new NamedQueriesDefinition(entityService.entityId,
                                                                                      TEST_ORG_ID,
                                                                                      applicationIdUsed,
                                                                                      projectIdUsed,
                                                                                      entityService.entityName,
                                                                                      [otherEntity, otherEntityAggregate, hiddenIndexAggregate, commentedAggregate, reindex, createTable]))

            await expect(entityService.namedQuery('otherEntity', [])).rejects.toThrow(/acts on Person, not Vehicle/)
            await expect(entityService.namedQuery('otherEntityAggregate', [])).rejects.toThrow(/acts on Person, not Vehicle/)
            await expect(entityService.namedQuery('hiddenIndexAggregate', [])).rejects.toThrow(/syntax error/)
            await expect(entityService.namedQuery('commentedAggregate', [])).rejects.toThrow(/syntax error/)
            await expect(entityService.namedQuery('reindex', [])).rejects.toThrow(/is not allowed/)
            await expect(entityService.namedQuery('createTable', [])).rejects.toThrow(/does not act on an entity/)
        }
    )

    it<LocalTestContext>(
        'Test Save Multiple',
        async ({entityService, applicationIdUsed, projectIdUsed}) => {
            const structureId = entityService.entityId
            const namedQueriesService = Kinotic.namedQueriesDefinitions

            const query = new QueryDecorator('SELECT COUNT(firstName) AS count FROM Person')
            const namedQuery = new FunctionDefinition('countAllPeople', [query])
            namedQuery.returnType = new ArrayC3Type(new ObjectC3Type('PeopleCount', applicationIdUsed)
                                                        .addProperty("count", new LongC3Type()))


            const query2 = new QueryDecorator('SELECT COUNT(firstName) AS count, lastName FROM Person WHERE lastName = :lastName GROUP BY lastName')
            const namedQuery2 = new FunctionDefinition('countPeopleByLastNameWithLastName', [query2])
            namedQuery2.addParameter('lastName', new StringC3Type())
            const contentType2 = new ObjectC3Type('CountByLastName', applicationIdUsed)
                .addProperty("count", new LongC3Type())
                .addProperty("lastName", new StringC3Type())
            namedQuery2.returnType = new ArrayC3Type(contentType2)


            const query3 = new QueryDecorator('SELECT COUNT(firstName) AS count, lastName FROM Person GROUP BY lastName')
            const namedQuery3 = new FunctionDefinition('countPeopleByLastNamePage', [query3])
            namedQuery3.addParameter('pageable', new PageableC3Type())
            const contentType3 = new ObjectC3Type('CountByLastName', applicationIdUsed)
                .addProperty("count", new LongC3Type())
                .addProperty("lastName", new StringC3Type())
            namedQuery3.returnType = new PageC3Type(contentType3)

            // Save the named queries
            const namedQueriesDefinition = new NamedQueriesDefinition(structureId,
                                                                      TEST_ORG_ID,
                                                                      applicationIdUsed,
                                                                      projectIdUsed,
                                                                      entityService.entityName,
                                                                      [namedQuery, namedQuery2, namedQuery3])
            await namedQueriesService.saveSync(namedQueriesDefinition)
        }
    )


})
