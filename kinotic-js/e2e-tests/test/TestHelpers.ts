import {faker} from '@faker-js/faker/locale/en'
import { EntityCodeGenerationService } from '@kinotic-ai/kinotic-cli/dist/internal/EntityCodeGenerationService.js'
import {ConsoleLogger} from '@kinotic-ai/kinotic-cli/dist/internal/Logger.js'
import {Kinotic, KinoticSingleton, Direction, Order, Pageable, IterablePage} from '@kinotic-ai/core'
import {
    ObjectC3Type,
    FunctionDefinition
} from '@kinotic-ai/idl'
import {randomUUID} from 'node:crypto'
import {expect} from 'vitest'
import {
    OsApiPlugin,
    EntityDefinition,
    KinoticProjectConfig,
    NamedQueriesDefinition,
    QueryDecorator,
    Project,
    IamUser,
    AuthType
} from '@kinotic-ai/os-api'
import {
    IEntityRepository,
    IAdminEntityRepository,
    PersistencePlugin
} from '@kinotic-ai/persistence'
import {Alert} from './domain/Alert.js'
import {Person} from './domain/Person.js'
import {inject} from 'vitest'
import path from 'path'
import {PersonWithTenant} from './domain/PersonWithTenant.js'
import {Cat, Dog} from './domain/Pet.js'
import {Vehicle, Wheel} from './domain/Vehicle.js'

Kinotic.use(OsApiPlugin)

const TEST_ORG_ID = 'kinotic-test'

type SchemaCreationResult ={
    entityDefinitionSchema: ObjectC3Type
    namedQueriesDefinition: NamedQueriesDefinition
}
let schemas: Map<string, SchemaCreationResult> = new Map<string, SchemaCreationResult>()

export async function initKinoticClient(): Promise<void> {
    try {
        // @ts-ignore
        const host = inject('KINOTIC_HOST')
        // @ts-ignore
        const port = inject('KINOTIC_PORT')

        console.log('Connecting to Kinotic at ' + host)

        await Kinotic.connect({
                                    host:host as string,
                                    port:port as number,
                                    connectHeaders:{login: 'kinotic@kinotic.local', passcode: 'kinotic', authScopeType: 'ORGANIZATION', authScopeId: 'kinotic-test'}
                                })

        console.log('Connected to Kinotic')
    } catch (e) {
        console.error(e)
        throw e
    }
}

export async function shutdownKinoticClient(): Promise<void> {
    try {
        await Kinotic.disconnect()
    } catch (e) {
        console.error(e)
        throw e
    }
}

/**
 * Ensures an APPLICATION-scoped IamUser exists for the given application and tenant. Must be
 * called while authenticated as an ORGANIZATION user (e.g. after {@link initKinoticClient}).
 * The user id is deterministic for a given (applicationId, tenantId) pair so repeated calls
 * are idempotent.
 *
 * @return the email of the provisioned user
 */
export async function createAppUserIfNotExist(applicationId: string, tenantId: string): Promise<string> {
    const email = `app-${applicationId}-${tenantId}@test.local`
    const existing = await Kinotic.iamUsers.findByEmailAndScope(email, 'APPLICATION', applicationId)
    if (existing == null) {
        const user = new IamUser()
        user.email = email
        user.displayName = `App Test User (${applicationId} / ${tenantId})`
        user.authType = AuthType.LOCAL
        user.authScopeType = 'APPLICATION'
        user.authScopeId = applicationId
        user.tenantId = tenantId
        await Kinotic.iamUsers.createUser(user, 'kinotic')
    }
    return email
}

/**
 * Creates a fresh {@link KinoticSingleton} connected as the APPLICATION-scoped user returned by
 * {@link createAppUserIfNotExist}. The caller is responsible for disconnecting it when done.
 * The instance has {@code OsApiPlugin} and {@code PersistencePlugin} installed so it can back
 * {@code EntityRepository} / {@code AdminEntityRepository} used to act on SHARED entity data.
 */
export async function initKinoticAppClient(applicationId: string, tenantId: string): Promise<KinoticSingleton> {
    const email = await createAppUserIfNotExist(applicationId, tenantId)
    // @ts-ignore
    const host = inject('KINOTIC_HOST') as string
    // @ts-ignore
    const port = inject('KINOTIC_PORT') as number

    const appKinotic = new KinoticSingleton()
    appKinotic.use(OsApiPlugin).use(PersistencePlugin)

    await appKinotic.connect({
        host: host,
        port: port,
        connectHeaders: {
            login: email,
            passcode: 'kinotic',
            authScopeType: 'APPLICATION',
            authScopeId: applicationId
        }
    })
    return appKinotic
}

export async function createPersonSchema(applicationId: string, projectId: string, withTenant: boolean = false): Promise<SchemaCreationResult> {
    return createSchema(applicationId, projectId, 'Person'+(withTenant ? 'WithTenant' : ''))
}

export async function createVehicleSchema(applicationId: string, projectId: string): Promise<SchemaCreationResult> {
    return createSchema(applicationId, projectId, 'Vehicle')
}

export async function createSchema(applicationId: string, projectId: string, entityName: string): Promise<SchemaCreationResult> {
    if(!schemas.has(entityName)){
        const codeGenerationService = new EntityCodeGenerationService(applicationId,
                                                                '.js',
                                                                new ConsoleLogger())

        const config = new KinoticProjectConfig()
        config.application = applicationId
        config.entitiesPaths = [{
            path: path.resolve(__dirname, './domain'),
            repositoryPath: path.resolve(__dirname, './repository'),
            mirrorFolderStructure: false
        }]
        config.validate = false
        config.fileExtensionForImports = ''
        
        await codeGenerationService
            .generateAllEntities(config,
                                 false,
                                 async (entityInfo, serviceInfos) =>{
                                     // combine named queries from generated services
                                     const namedQueries: FunctionDefinition[] = []
                                     for(let serviceInfo of serviceInfos){
                                            namedQueries.push(...serviceInfo.namedQueries)
                                     }
                                     const id = (TEST_ORG_ID + '.' + applicationId + '.' + entityName).toLowerCase()
                                     const result: SchemaCreationResult = {
                                        entityDefinitionSchema: entityInfo.entity,
                                        namedQueriesDefinition: new NamedQueriesDefinition(id,
                                                                                           applicationId,
                                                                                           projectId,
                                                                                           entityName,
                                                                                           namedQueries)
                                     }
                                     schemas.set(entityInfo.entity.name, result)
                                 },true)
    }
    const result = schemas.get(entityName)
    if(!result){
        throw new Error('Could not find Entity ' + entityName)
    }
    const ret = structuredClone(result)
    if(!ret){
        throw new Error('Could not copy schema for ' + entityName)
    }

    ret.entityDefinitionSchema.name = entityName
    ret.namedQueriesDefinition.id = (TEST_ORG_ID + '.' + applicationId + '.' + entityName).toLowerCase()
    ret.namedQueriesDefinition.entityDefinitionName = entityName
    replaceAllQueryPlaceholdersWithId(TEST_ORG_ID + '.' + applicationId + '.' + entityName, ret.namedQueriesDefinition.namedQueries)
    return ret
}

/**
 * This replaces the PLACEHOLDER string in all @Query decorators applied to the given function definitions
 * @param structureId to replace the PLACEHOLDER with
 * @param functionDefinitions all of the {@link FunctionDefinition}s to replace the PLACEHOLDER in
 */
function replaceAllQueryPlaceholdersWithId(structureId: string, functionDefinitions: FunctionDefinition[]){
    for(const functionDefinition of functionDefinitions){
        if(functionDefinition.decorators) {
            for (const decorator of functionDefinition.decorators) {
                if (decorator.type === 'Query') {
                    const queryDecorator = decorator as QueryDecorator
                    // @ts-ignore stupid intellij error for replaceAll
                    queryDecorator.statements = queryDecorator.statements.replaceAll('PLACEHOLDER', structureId.toLowerCase())
                }
            }
        }
    }
}

// Add these new functions to your existing TestHelpers.ts file

export async function createAlertEntityDefinitionIfNotExist(applicationId: string, projectName: string): Promise<EntityDefinition> {
    const entityDefinitionId = TEST_ORG_ID + '.' + applicationId + '.alert'
    let entityDefinition = await Kinotic.entityDefinitions.findById(entityDefinitionId)
    if (entityDefinition == null) {
        entityDefinition = await createAlertEntityDefinition(applicationId, projectName)
    }
    return entityDefinition
}

export async function createAlertEntityDefinition(applicationId: string, projectName: string): Promise<EntityDefinition> {

    await Kinotic.applications.createApplicationIfNotExist(applicationId, 'Application')

    let project: Project = new Project(null, applicationId, projectName, 'Project')
    project = await Kinotic.projects.createProjectIfNotExist(project)

    const {entityDefinitionSchema} = await createAlertSchema(applicationId, project.id as string)
    const alertEntityDefinition = new EntityDefinition(
        applicationId,
        project.id as string,
        'Alert',
        entityDefinitionSchema,
        'System alerts and notifications stream'
    )

    const savedEntityDefinition = await Kinotic.entityDefinitions.create(alertEntityDefinition)

    if (savedEntityDefinition.id) {
        await Kinotic.entityDefinitions.publish(savedEntityDefinition.id)
    } else {
        throw new Error('No EntityDefinition id')
    }

    return savedEntityDefinition
}

export async function createAlertSchema(applicationId: string, projectId: string): Promise<SchemaCreationResult> {
    return createSchema(applicationId, projectId, 'Alert')
}

// Add this helper function to create test Alert instances
export function createTestAlert(options: Partial<Alert> & { index?: number } = {}): Alert {
    const index = options.index ?? 0
    const ret = new Alert()
    ret.alertId = options.alertId ?? `alert-${index.toString().padStart(3, '0')}`
    ret.message = options.message ?? faker.lorem.sentence()
    ret.severity = options.severity ?? (index % 3 === 0 ? 'LOW' : index % 3 === 1 ? 'MEDIUM' : 'HIGH')
    ret.source = options.source ?? faker.internet.domainName()
    ret.timestamp = options.timestamp ?? new Date(Date.now() - (index * 1000))
    ret.active = options.active ?? (index % 2 === 0)
    return ret
}

// Add this helper function to create multiple test Alerts
export function createTestAlerts(numberToCreate: number): Alert[] {
    const ret: Alert[] = []
    for (let i = 0; i < numberToCreate; i++) {
        ret.push(createTestAlert({index: i}))
    }
    return ret
}

export async function createPersonEntityDefinitionIfNotExist(applicationId: string, projectName: string, withTenant: boolean = false): Promise<EntityDefinition>{
    const structureId = TEST_ORG_ID + '.' + applicationId + '.person' + ( withTenant ? 'withtenant' : '')
    let entityDefinition = await Kinotic.entityDefinitions.findById(structureId)
    if(entityDefinition == null){
        entityDefinition = await createPersonEntityDefinition(applicationId, projectName, withTenant)
    }
    return entityDefinition
}

export async function createPersonEntityDefinition(applicationId: string, projectName: string, withTenant: boolean = false): Promise<EntityDefinition>{

    await Kinotic.applications.createApplicationIfNotExist(applicationId, 'Application')

    let project: Project = new Project(null, applicationId, projectName, 'Project')
    project = await Kinotic.projects.createProjectIfNotExist(project)

    const {entityDefinitionSchema} = await createPersonSchema(applicationId, project.id as string, withTenant)
    const personEntityDefinition = new EntityDefinition(applicationId,
                                                        project.id as string,
                                                        'Person' + (withTenant ? 'WithTenant' : ''),
                                                        entityDefinitionSchema,
                                                        'Tracks people that are going to mars')

    const savedEntityDefinition = await Kinotic.entityDefinitions.create(personEntityDefinition)

    if(savedEntityDefinition.id) {
        await Kinotic.entityDefinitions.publish(savedEntityDefinition.id)
    }else{
        throw new Error('No Structure id')
    }

    return savedEntityDefinition
}

export async function createVehicleEntityDefinitionIfNotExist(applicationId: string, projectName: string): Promise<EntityDefinition>{
    const entityDefinitionId = TEST_ORG_ID + '.' + applicationId + '.vehicle'
    let entityDefinition = await Kinotic.entityDefinitions.findById(entityDefinitionId)
    if(entityDefinition == null){
        entityDefinition = await createVehicleEntityDefinition(applicationId, projectName)
    }
    return entityDefinition
}

export async function createVehicleEntityDefinition(applicationId: string, projectName: string): Promise<EntityDefinition>{

    await Kinotic.applications.createApplicationIfNotExist(applicationId, 'Application')
    console.log('Created application', applicationId);
    let project: Project = new Project(null, applicationId, projectName, 'Project')
    project = await Kinotic.projects.createProjectIfNotExist(project)
    console.log('Created project', project.id);
    const {entityDefinitionSchema} = await createVehicleSchema(applicationId, project.id as string)
    console.log('Created entity definition', entityDefinitionSchema);
    const vehicleEntityDefinition = new EntityDefinition(applicationId,
                                                         project.id as string,
                                                         'Vehicle',
                                                         entityDefinitionSchema,
                                                         'Some form of transportation')
    console.log('Created vehicle EntityDefinition', vehicleEntityDefinition);
    const savedEntityDefinition = await Kinotic.entityDefinitions.create(vehicleEntityDefinition)
    console.log('Saved EntityDefinition', savedEntityDefinition);
    if(savedEntityDefinition.id) {
        await Kinotic.entityDefinitions.publish(savedEntityDefinition.id)
        console.log('Published entityDefinition', savedEntityDefinition.id);
    }else{
        throw new Error('No Structure id')
    }

    return savedEntityDefinition
}


export async function deleteEntityDefinition(entityDefinitionId: string): Promise<void>{
    await Kinotic.entityDefinitions.unPublish(entityDefinitionId)
    await Kinotic.entityDefinitions.deleteById(entityDefinitionId)
}

export function createTestPeople(numberToCreate: number): Person[] {
    const ret: Person[] = []
    for (let i = 0; i < numberToCreate; i++) {
        ret.push(createTestPerson(i))
    }
    return ret
}

export async function createTestPeopleAndVerify(entityService: IEntityRepository<Person>,
                                                numberToCreate: number): Promise<void> {
    // Create people
    const people: Person[] = createTestPeople(numberToCreate)
    await expect(entityService.bulkSave(people)).resolves.toBeNull()
    await expect(entityService.syncIndex()).resolves.toBeNull()

    // Count the people
    await expect(entityService.count()).resolves.toBe(numberToCreate)
}

export function createTestPeopleWithTenant(numberToCreate: number, tenantId: string): PersonWithTenant[] {
    const ret: PersonWithTenant[] = []
    for (let i = 0; i < numberToCreate; i++) {
        ret.push(createTestPersonWithTenant(i, tenantId))
    }
    return ret
}

export async function createTestPeopleWithTenantAndVerify(adminEntityService: IAdminEntityRepository<PersonWithTenant>,
                                                          entityService: IEntityRepository<PersonWithTenant>,
                                                          tenantId: string,
                                                          numberToCreate: number): Promise<void> {
    // Create people
    const people: PersonWithTenant[] = createTestPeopleWithTenant(numberToCreate, tenantId)
    await expect(entityService.bulkSave(people)).resolves.toBeNull()
    await expect(entityService.syncIndex()).resolves.toBeNull()

    // Count the people
    await expect(adminEntityService.count([tenantId])).resolves.toBe(numberToCreate)
}

export async function findAndVerifyPeopleWithCursorPaging(entityService: IEntityRepository<Person>,
                                                          numberToExpect: number){
    let elementsFound = 0
    const pageable = Pageable.createWithCursor(null,
                                               10,
                                               { orders: [
                                                       new Order('firstName', Direction.ASC),
                                                       new Order('id', Direction.ASC)
                                                   ] })
    const firstPage: IterablePage<Person> = await entityService.findAll(pageable)
    expect(firstPage).toBeDefined()
    for await(const page of firstPage){
        // @ts-ignore
        elementsFound += page.content.length
    }
    expect(elementsFound, `Should have found ${numberToExpect} Entities`).toBe(numberToExpect)
}

export async function findAndVerifyPeopleWithOffsetPaging(entityService: IEntityRepository<Person>,
                                                          numberToExpect: number){
    let elementsFound = 0
    const pageable = Pageable.create(0,
                                     10,
                                     { orders: [
                                             new Order('firstName', Direction.ASC),
                                             new Order('id', Direction.ASC)
                                         ] })
    const firstPage: IterablePage<Person> = await entityService.findAll(pageable)
    expect(firstPage).toBeDefined()
    for await(const page of firstPage){
        // @ts-ignore
        elementsFound += page.content.length
    }
    expect(elementsFound, `Should have found ${numberToExpect} Entities`).toBe(numberToExpect)
}

export function createTestPersonWithTenant(index: number = 0, tenantId: string): PersonWithTenant {
    let ret: PersonWithTenant = new PersonWithTenant()
    addDataToPerson(index, ret)
    ret.tenantId = tenantId
    return ret
}

export function createTestPerson(index: number = 0): Person {
    let ret: Person = new Person()
    addDataToPerson(index, ret)
    return ret
}

function addDataToPerson(index: number = 0, person: Person | PersonWithTenant){
    if(index % 2 === 0){
        person.firstName = 'John'
        person.lastName = 'Doe'
        person.myPet = new Cat()
        person.myPet.age = 4
        person.myPet.name = 'Fluffy'
    }else{
        person.firstName = 'Steve'
        person.lastName = 'Wozniak'
        person.myPet = new Dog()
        person.myPet.age = 10
        person.myPet.name = 'Zapato'
    }
    person.age = 42
    person.address = {
        street: '123 Main St',
        city: 'Anytown',
        state: 'CA',
        zip: '12345'
    }
}

export function createTestVehicles(numberToCreate: number): Vehicle[] {
    const ret: Vehicle[] = []
    for (let i = 0; i < numberToCreate; i++) {
        ret.push(createTestVehicle())
    }
    return ret
}

export function createTestVehicle(): Vehicle {
    const ret = new Vehicle();
    ret.id = randomUUID()
    ret.manufacturer = faker.vehicle.manufacturer()
    ret.model = faker.vehicle.model()
    ret.color = faker.vehicle.color()
    ret.wheelType = new Wheel()
    ret.wheelType.brand = 'BFG'
    ret.wheelType.size = 35
    return ret
}

export function generateRandomString(length: number){
    let result = ''
    const characters =
              'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz'
    const charactersLength = characters.length
    for (let i = 0; i < length; i++) {
        result += characters.charAt(Math.floor(Math.random() * charactersLength))
    }
    return result
}

/**
 * Logs the failure of a promise and then rethrows the error
 * @param promise to log failure of
 * @param message to log
 */
export async function logFailure<T>(promise: Promise<T>, message: string): Promise<T> {
    try {
        return await promise
    } catch (e) {
        console.error(message, e)
        throw e
    }
}
