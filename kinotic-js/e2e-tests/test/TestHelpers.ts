import {AppApiPlugin} from '@kinotic-ai/app-api'
import {faker} from '@faker-js/faker/locale/en'
import { EntityCodeGenerationService } from '@kinotic-ai/kinotic-cli/dist/internal/EntityCodeGenerationService.js'
import {ConsoleLogger} from '@kinotic-ai/kinotic-cli/dist/internal/Logger.js'
import {BasicCredentialsResolver, buildBrokerUrl, buildServerUrl, type ConnectOptions, type CredentialsResolver, Kinotic, KinoticSingleton, Direction, Order, Pageable, IterablePage, type ServerInfo, SessionKeepAliveMode} from '@kinotic-ai/core'
import {ensureNodeWebSocket} from '@kinotic-ai/core/node'
import {
    ObjectC3Type,
    FunctionDefinition
} from '@kinotic-ai/idl'
import {randomUUID} from 'node:crypto'
import {expect} from 'vitest'
import {
    ManagementApiPlugin,
    EntityDefinition,
    KinoticProjectConfig,
    NamedQueriesDefinition,
    Project,
    type Resource,
    type Subject,
    SubjectKind
} from '@kinotic-ai/management-api'
import {
    IEntityRepository,
    IAdminEntityRepository,
    PersistencePlugin
} from '@kinotic-ai/persistence'
import {Alert} from './domain/Alert.js'
import {Person} from './domain/Person.js'
import {inject} from 'vitest'
import type {KinoticServerName} from './setup.js'
import path from 'path'
import {PersonWithTenant} from './domain/PersonWithTenant.js'
import {Cat, Dog} from './domain/Pet.js'
import {Vehicle, Wheel} from './domain/Vehicle.js'

// header-credential connects need a WebSocket constructor that accepts upgrade headers
ensureNodeWebSocket()

Kinotic.use(ManagementApiPlugin)

type SchemaCreationResult ={
    entityDefinitionSchema: ObjectC3Type
    namedQueriesDefinition: NamedQueriesDefinition
}
let schemas: Map<string, SchemaCreationResult> = new Map<string, SchemaCreationResult>()

/** Organization that owns every e2e fixture user (V3 test users + V5 app fixtures). */
export const E2E_ORGANIZATION_ID = 'kinotic-test'

/** Password of every user the e2e migrations seed. */
export const E2E_FIXTURE_PASSWORD = 'kinotic'

/** Tenant id of the V5-seeded APPLICATION-scope fixture users. */
export const E2E_APP_TENANT = 'kinotic'

/** The seeded ORGANIZATION-scope admin of the e2e organization. */
export const E2E_ORG_USER_EMAIL = 'kinotic@kinotic.local'

/** The seeded SYSTEM-scope admin. */
export const E2E_SYSTEM_USER_EMAIL = 'admin@kinotic.local'

/** Email convention of the V5-seeded APPLICATION-scope fixture users. */
export function appFixtureEmail(applicationId: string, tenantId: string): string {
    return `app-${applicationId}-${tenantId}@test.local`
}

export function kinoticHost(): string {
    // @ts-ignore
    return inject('KINOTIC_HOST') as string
}

/** The e2e stack's server with the given compose service name, as a {@link ServerInfo} — the suite always runs without TLS. */
export function kinoticServer(name: KinoticServerName): ServerInfo {
    // @ts-ignore
    const port = (inject('KINOTIC_PORTS') as Partial<Record<KinoticServerName, number>>)[name]
    if (port === undefined) {
        throw new Error(`The suite's setup did not start ${name}`)
    }
    return {host: kinoticHost(), port, useSSL: false}
}

/** The management server, where an organization's users and machines connect. */
export function managementServer(): ServerInfo {
    return kinoticServer('kinotic-server-management')
}

/** The system server, where platform operators connect. */
export function systemServer(): ServerInfo {
    return kinoticServer('kinotic-server-system')
}

/** The app server, where an application's users and machines and an organization's runtimes connect. */
export function appServer(): ServerInfo {
    return kinoticServer('kinotic-server-app')
}

/** REST base URL of the given server, the management server by default. */
export function restBase(server: ServerInfo = managementServer()): string {
    return buildServerUrl(server, 'http')
}

/** STOMP broker URL of the given server, the management server by default. */
export function stompUrl(server: ServerInfo = managementServer()): string {
    return buildBrokerUrl(server)
}

/** POSTs an application/x-www-form-urlencoded body — the shape of every OAuth endpoint call. */
export function postForm(url: string, params: Record<string, string>): Promise<Response> {
    return fetch(url, {
        method: 'POST',
        headers: {'Content-Type': 'application/x-www-form-urlencoded'},
        body: new URLSearchParams(params)
    })
}

export function buildConnectOptions(credentials: CredentialsResolver, server: ServerInfo = managementServer()): ConnectOptions {
    return {
        server,
        sessionKeepAlive: SessionKeepAliveMode.NONE,
        credentials
    }
}

export async function initKinoticClient(): Promise<void> {
    try {
        console.log('Connecting to Kinotic at ' + kinoticHost())

        await Kinotic.connect(buildConnectOptions(
            new BasicCredentialsResolver(E2E_ORG_USER_EMAIL, E2E_FIXTURE_PASSWORD, E2E_ORGANIZATION_ID)))

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
 * Creates a fresh {@link KinoticSingleton} connected to the app server as the APPLICATION-scoped user
 * seeded for the given (applicationId, tenantId) pair by the V3__e2e_app_fixtures migration (email
 * convention app-<applicationId>-<tenantId>@test.local, password kinotic), granted the tenant admin role
 * on its tenant first, so it reads and writes every definition's rows there, and connected once the
 * application's store answers for each entity named within the tenant. The caller is responsible for disconnecting it
 * when done. The instance has {@code ManagementApiPlugin}, {@code PersistencePlugin} and {@code AppApiPlugin}
 * installed so it can back an {@code EntityRepository} that acts on the SHARED entity data of its own tenant
 * and call the tenant services for it.
 *
 * @param entityNames the names of the entity definitions the client will act on, each published already
 */
export async function initKinoticAppClient(applicationId: string, tenantId: string, ...entityNames: string[]): Promise<KinoticSingleton> {
    await grantTenantAdmin(applicationId, tenantId, entityNames)
    return connectAppClient(applicationId, tenantId)
}

/**
 * Connects a fresh {@link KinoticSingleton} to the app server as the fixture user of the (applicationId, tenantId)
 * pair, holding whatever the application has granted it so far.
 */
export async function connectAppClient(applicationId: string, tenantId: string): Promise<KinoticSingleton> {
    const appKinotic = new KinoticSingleton()
    appKinotic.use(ManagementApiPlugin).use(PersistencePlugin).use(AppApiPlugin)

    await appKinotic.connect(buildConnectOptions(
        new BasicCredentialsResolver(appFixtureEmail(applicationId, tenantId),
                                     E2E_FIXTURE_PASSWORD, E2E_ORGANIZATION_ID, applicationId),
        appServer()))
    return appKinotic
}

/**
 * The fixture user of the (applicationId, tenantId) pair as a grant's subject, found among the application's users
 * by its email.
 */
export async function appFixtureSubject(applicationId: string, tenantId: string): Promise<Subject> {
    const email = appFixtureEmail(applicationId, tenantId)
    const users = await Kinotic.members.findMembers(applicationId, Pageable.create(0, 100))
    const user = users.content?.find(member => member.email === email)
    if (!user?.id) {
        throw new Error(`No user ${email} is seeded for application ${applicationId}`)
    }
    return {kind: SubjectKind.USER, id: user.id}
}

/**
 * Grants the fixture user of the (applicationId, tenantId) pair the tenant admin role on its tenant, which reaches
 * the rows of every definition of the application in that tenant; then waits until the store answers for each
 * entity named within the tenant, which the definition's creation placed in the store. A user already holding the
 * role is granted nothing again.
 */
export async function grantTenantAdmin(applicationId: string, tenantId: string, entityNames: string[]): Promise<void> {
    const subject = await appFixtureSubject(applicationId, tenantId)
    const tenant: Resource = {type: 'tenant', id: tenantId}
    await until(async () => {
        const grants = await Kinotic.applicationAccess.findGrants(applicationId, tenant)
        if (!grants.some(grant => grant.roleId === 'tenant.admin' && grant.subject.id === subject.id)) {
            await Kinotic.applicationAccess.grant(applicationId, subject, 'tenant.admin', tenant)
        }
        return true
    })
    for (const entityName of entityNames) {
        const definitionId = `${E2E_ORGANIZATION_ID}.${applicationId}.${entityName}`.toLowerCase()
        const rows: Resource = {type: 'tenant_definition', id: `${definitionId}@${tenantId}`}
        await until(async () => (await Kinotic.applicationAccess.explain(applicationId, subject, 'entity_definition_can_read', rows)).allowed)
    }
}

/**
 * Polls the call until it is admitted, for up to the timeout, and returns what it answers: the gateway answers
 * from the engine's caches, which lag a grant by a moment.
 */
export async function untilAdmitted<T>(call: () => Promise<T>, timeoutMs: number = 30000): Promise<T> {
    let ret!: T
    await until(async () => {
        ret = await call()
        return true
    }, timeoutMs)
    return ret
}

/**
 * Polls the call until it is refused with a message matching the pattern, for up to the timeout: the gateway
 * answers from the engine's caches, which lag a revocation by a moment. A refusal for any other reason fails
 * the wait.
 */
export async function untilRefused(call: () => Promise<unknown>, pattern: RegExp, timeoutMs: number = 30000): Promise<void> {
    await until(async () => {
        let ret = false
        try {
            await call()
        } catch (e) {
            const message = e instanceof Error ? e.message : String(e)
            if (!pattern.test(message)) {
                throw e
            }
            ret = true
        }
        return ret
    }, timeoutMs)
}

/**
 * Polls the condition every quarter second until it holds, for up to the timeout; a condition that throws is
 * polled again.
 */
export async function until(condition: () => Promise<boolean>, timeoutMs: number = 30000): Promise<void> {
    const deadline = Date.now() + timeoutMs
    let last: unknown
    while (Date.now() < deadline) {
        try {
            if (await condition()) {
                return
            }
        } catch (e) {
            last = e
        }
        await new Promise(resolve => setTimeout(resolve, 250))
    }
    throw new Error(`Timed out after ${timeoutMs}ms waiting for ${condition}` + (last ? `; last failure: ${last}` : ''))
}

export async function createPersonSchema(organizationId: string, applicationId: string, projectId: string, withTenant: boolean = false): Promise<SchemaCreationResult> {
    return createSchema(organizationId, applicationId, projectId, 'Person'+(withTenant ? 'WithTenant' : ''))
}

export async function createVehicleSchema(organizationId: string, applicationId: string, projectId: string): Promise<SchemaCreationResult> {
    return createSchema(organizationId, applicationId, projectId, 'Vehicle')
}

export async function createSchema(organizationId: string, applicationId: string, projectId: string, entityName: string): Promise<SchemaCreationResult> {
    if(!schemas.has(entityName)){
        const codeGenerationService = new EntityCodeGenerationService(applicationId,
                                                                '.js',
                                                                new ConsoleLogger())

        const config = new KinoticProjectConfig()
        config.organizationId = organizationId
        config.applicationId = applicationId
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
                                     const id = (organizationId + '.' + applicationId + '.' + entityName).toLowerCase()
                                     const result: SchemaCreationResult = {
                                        entityDefinitionSchema: entityInfo.entity,
                                        namedQueriesDefinition: new NamedQueriesDefinition(id,
                                                                                           organizationId,
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
    ret.namedQueriesDefinition.id = (organizationId + '.' + applicationId + '.' + entityName).toLowerCase()
    ret.namedQueriesDefinition.organizationId = organizationId
    ret.namedQueriesDefinition.entityDefinitionName = entityName
    return ret
}

// Add these new functions to your existing TestHelpers.ts file

export async function createAlertEntityDefinitionIfNotExist(organizationId: string, applicationId: string, projectName: string): Promise<EntityDefinition> {
    const entityDefinitionId = organizationId + '.' + applicationId + '.alert'
    let entityDefinition = await Kinotic.entityDefinitions.findById(entityDefinitionId)
    if (entityDefinition == null) {
        entityDefinition = await createAlertEntityDefinition(organizationId, applicationId, projectName)
    }
    return entityDefinition
}

export async function createAlertEntityDefinition(organizationId: string, applicationId: string, projectName: string): Promise<EntityDefinition> {

    await Kinotic.applications.createApplicationIfNotExist(applicationId, 'Application')

    let project: Project = new Project(null, applicationId, projectName, 'Project')
    project.organizationId = organizationId
    project = await Kinotic.projects.createProjectIfNotExist(project)

    const {entityDefinitionSchema} = await createAlertSchema(organizationId, applicationId, project.id as string)
    const alertEntityDefinition = new EntityDefinition(
        organizationId,
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

export async function createAlertSchema(organizationId: string, applicationId: string, projectId: string): Promise<SchemaCreationResult> {
    return createSchema(organizationId, applicationId, projectId, 'Alert')
}

// Add this helper function to create test Alert instances
export function createTestAlert(options: Partial<Alert> & { index?: number } = {}): Alert {
    const index = options.index ?? 0
    const ret = new Alert()
    ret.alertId = options.alertId ?? `alert-${index.toString().padStart(3, '0')}`
    ret.message = options.message ?? faker.lorem.sentence()
    ret.severity = options.severity ?? (index % 3 === 0 ? 'LOW' : index % 3 === 1 ? 'MEDIUM' : 'HIGH')
    ret.source = options.source ?? faker.internet.domainName()
    ret.timestamp = options.timestamp ?? new Date(Date.now() - (index * 1000)).toISOString()
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

export async function createPersonEntityDefinitionIfNotExist(organizationId: string, applicationId: string, projectName: string, withTenant: boolean = false): Promise<EntityDefinition>{
    const structureId = organizationId + '.' + applicationId + '.person' + ( withTenant ? 'withtenant' : '')
    let entityDefinition = await Kinotic.entityDefinitions.findById(structureId)
    if(entityDefinition == null){
        entityDefinition = await createPersonEntityDefinition(organizationId, applicationId, projectName, withTenant)
    }
    return entityDefinition
}

export async function createPersonEntityDefinition(organizationId: string, applicationId: string, projectName: string, withTenant: boolean = false): Promise<EntityDefinition>{

    await Kinotic.applications.createApplicationIfNotExist(applicationId, 'Application')

    let project: Project = new Project(null, applicationId, projectName, 'Project')
    project.organizationId = organizationId
    project = await Kinotic.projects.createProjectIfNotExist(project)

    const {entityDefinitionSchema} = await createPersonSchema(organizationId, applicationId, project.id as string, withTenant)
    const personEntityDefinition = new EntityDefinition(organizationId,
                                                        applicationId,
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

export async function createVehicleEntityDefinitionIfNotExist(organizationId: string, applicationId: string, projectName: string): Promise<EntityDefinition>{
    const entityDefinitionId = organizationId + '.' + applicationId + '.vehicle'
    let entityDefinition = await Kinotic.entityDefinitions.findById(entityDefinitionId)
    if(entityDefinition == null){
        entityDefinition = await createVehicleEntityDefinition(organizationId, applicationId, projectName)
    }
    return entityDefinition
}

export async function createVehicleEntityDefinition(organizationId: string, applicationId: string, projectName: string): Promise<EntityDefinition>{

    await Kinotic.applications.createApplicationIfNotExist(applicationId, 'Application')
    console.log('Created application', applicationId);
    let project: Project = new Project(null, applicationId, projectName, 'Project')
    project.organizationId = organizationId
    project = await Kinotic.projects.createProjectIfNotExist(project)
    console.log('Created project', project.id);
    const {entityDefinitionSchema} = await createVehicleSchema(organizationId, applicationId, project.id as string)
    console.log('Created entity definition', entityDefinitionSchema);
    const vehicleEntityDefinition = new EntityDefinition(organizationId,
                                                         applicationId,
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
