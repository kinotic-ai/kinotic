import {Kinotic, KinoticSingleton, Pageable} from '@kinotic-ai/core'
import {EntityDefinition, type Resource, type Subject} from '@kinotic-ai/management-api'
import {EntitiesRepository, EntityRepository, IEntityRepository} from '@kinotic-ai/persistence'
import * as allure from 'allure-js-commons'
import {afterAll, beforeAll, describe, expect, it} from 'vitest'
import {Person} from '../domain/Person.js'
import {Vehicle} from '../domain/Vehicle.js'
import {
    E2E_APP_TENANT as APP_TENANT,
    E2E_ORGANIZATION_ID as TEST_ORG_ID,
    appFixtureSubject,
    connectAppClient,
    createPersonEntityDefinitionIfNotExist,
    createTestPerson,
    createVehicleEntityDefinitionIfNotExist,
    deleteEntityDefinition,
    generateRandomString,
    initKinoticClient,
    shutdownKinoticClient,
    until,
    untilAdmitted,
    untilRefused
} from '../TestHelpers.js'

// Fixed id: the app client logs in as app-<APP_ID>-<APP_TENANT>@test.local, an APPLICATION-scoped
// user that V3__e2e_app_fixtures seeds only for this applicationId.
const APP_ID = 'e2e-end-user-access'

/**
 * Covers enforcement of an application's grants on its end users, in the application's own store: the fixture
 * user, granted nothing, is refused the rows of a definition; granted the editor role of a definition's rows on
 * the definition within its tenant, it reads and writes them and is still refused another definition's rows;
 * revoked, it is refused again. The organization's administrator grants and revokes through
 * ApplicationAccessService; the user acts through its own connection.
 */
describe('Kinotic JS', () => {

    let appKinotic: KinoticSingleton
    let person: EntityDefinition
    let vehicle: EntityDefinition
    let subject: Subject
    let people: IEntityRepository<Person>
    let vehicles: IEntityRepository<Vehicle>
    // a definition's rows within the fixture user's tenant, the place a grant on them alone is made
    let personRows: Resource
    let vehicleRows: Resource

    beforeAll(async () => {
        await allure.suite('e2e-tests/native')
        await allure.subSuite('EndUserAccess')
        // the organization's administrator (kinotic@kinotic.local) creates the fixtures and makes the grants
        await initKinoticClient()
        const projectName = generateRandomString(5)
        person = await createPersonEntityDefinitionIfNotExist(TEST_ORG_ID, APP_ID, projectName)
        vehicle = await createVehicleEntityDefinitionIfNotExist(TEST_ORG_ID, APP_ID, projectName)
        subject = await appFixtureSubject(APP_ID, APP_TENANT)
        appKinotic = await connectAppClient(APP_ID, APP_TENANT)
        people = new EntityRepository(TEST_ORG_ID, APP_ID, person.name, new EntitiesRepository(appKinotic))
        vehicles = new EntityRepository(TEST_ORG_ID, APP_ID, vehicle.name, new EntitiesRepository(appKinotic))
        personRows = {type: 'tenant_definition', id: `${person.id}@${APP_TENANT}`}
        vehicleRows = {type: 'tenant_definition', id: `${vehicle.id}@${APP_TENANT}`}
    }, 300000)

    afterAll(async () => {
        await appKinotic?.disconnect()
        for (const definition of [person, vehicle]) {
            if (definition?.id) {
                await deleteEntityDefinition(definition.id)
            }
        }
        await Kinotic.entityDefinitions.syncIndex()
        if (person?.projectId) {
            await Kinotic.projects.deleteById(person.projectId)
            await Kinotic.projects.syncIndex()
        }
        await Kinotic.applications.deleteById(APP_ID)
        await shutdownKinoticClient()
    }, 120000)

    it('refuses an end user granted nothing', async () => {
        // the store holds the roles of a definition's rows once its worker has written the model
        await until(async () => (await Kinotic.applicationAccess.findRoles(APP_ID)).some(role => role.id === 'entity_definition.editor'))
        expect((await Kinotic.applicationAccess.findGrants(APP_ID, personRows))).toHaveLength(0)
        await expect(people.findAll(Pageable.create(0, 10))).rejects.toThrowError(/entity_definition_can_search/)
        await expect(people.save(createTestPerson())).rejects.toThrowError(/entity_definition_can_create/)
        expect((await Kinotic.applicationAccess.explain(APP_ID, subject, 'entity_definition_can_read', personRows)).allowed).toBe(false)
    }, 60000)

    it('admits the rows of a definition granted within the tenant and no other', async () => {
        const grant = await Kinotic.applicationAccess.grant(APP_ID, subject, 'entity_definition.editor', personRows)
        expect(grant.id).toBeTruthy()
        expect(grant.resource).toEqual(personRows)
        await until(async () => (await Kinotic.applicationAccess.explain(APP_ID, subject, 'entity_definition_can_read', personRows)).allowed)

        const saved = await untilAdmitted(() => people.save(createTestPerson()))
        expect(saved.id).toBeTruthy()
        const found = await people.findById(saved.id as string)
        expect(found?.firstName).toBe(saved.firstName)
        expect(await people.count()).toBeGreaterThan(0)
        // an editor does not delete, and holds nothing on another definition's rows
        await expect(people.deleteById(saved.id as string)).rejects.toThrowError(/entity_definition_can_delete/)
        await expect(vehicles.findAll(Pageable.create(0, 10))).rejects.toThrowError(`entity_definition_can_search on tenant_definition:${vehicleRows.id}`)

        // the grant is listed where it was made and explains the access
        const grants = await Kinotic.applicationAccess.findGrants(APP_ID, personRows)
        expect(grants.map(g => g.id)).toEqual([grant.id])
        const explained = await Kinotic.applicationAccess.explain(APP_ID, subject, 'entity_definition_can_edit', personRows)
        expect(explained.allowed).toBe(true)
        expect(explained.through.map(g => g.id)).toEqual([grant.id])

        await Kinotic.applicationAccess.revoke(APP_ID, personRows, grant.id)
        await until(async () => !(await Kinotic.applicationAccess.explain(APP_ID, subject, 'entity_definition_can_read', personRows)).allowed)
        await untilRefused(() => people.findById(saved.id as string), /entity_definition_can_read/)
        expect(await Kinotic.applicationAccess.findGrants(APP_ID, personRows)).toHaveLength(0)
    }, 60000)

    it('lists the roles the application defines, the same for every definition', async () => {
        const roles = await Kinotic.applicationAccess.findRoles(APP_ID)
        const ids = roles.map(role => role.id)
        expect(ids).toEqual(expect.arrayContaining(['entity_definition.viewer', 'entity_definition.editor', 'entity_definition.admin', 'tenant.admin', 'application.admin']))
        const tenantAdmin = roles.find(role => role.id === 'tenant.admin')
        expect(tenantAdmin?.builtIn).toBe(true)
        expect(tenantAdmin?.permissions).toEqual(expect.arrayContaining(['entity_definition_can_delete']))
    })
})
