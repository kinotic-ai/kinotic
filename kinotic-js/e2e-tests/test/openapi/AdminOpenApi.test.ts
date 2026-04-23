import {Kinotic} from '@kinotic-ai/core'
import {EntityDefinition, IamUser, AuthType} from '@kinotic-ai/os-api'
import axios from 'axios'
import {afterAll, beforeAll, describe, expect, inject, it} from 'vitest'
import {PersonWithTenant} from '../domain/PersonWithTenant.js'
import {createPersonEntityDefinitionIfNotExist, createSchema, initKinoticClient, shutdownKinoticClient} from '../TestHelpers.js'
import {
    buildFirstNameQuery,
    bulkSavePeople,
    countWithTenants,
    generatePeopleForTenants,
    generatePerson,
    loadOpenAPISchema,
    searchWithTenants,
    syncIndex
} from './OpenApiHelpers.js'
import { faker } from '@faker-js/faker'

interface LocalTestContext {
    personWithTenantStructure: EntityDefinition
}

const TEST_ORG_ID = 'kinotic-test'
const applicationId = 'openapi.admin'
const projectName = 'TestProject'
const DEFAULT_TENANT = 'kinotic'
const APP_USER_EMAIL = 'app-test@kinotic.local'
const APP_USER_PASSWORD = 'kinotic'

// Org-scoped axios instance — used for /admin/api/... routes that explicitly select tenants.
// The org user has no tenantId of its own; tenant routing comes from the admin tenantSelection.
const axiosInstance = axios.create({
                                       headers: {
                                           'login': 'kinotic@kinotic.local',
                                           'passcode': 'kinotic',
                                           'authScopeType': 'ORGANIZATION',
                                           'authScopeId': 'kinotic-test',
                                           'Content-Type': 'application/json'
                                       }
                                   })

// Application-scoped axios instance — used for /api/... routes against SHARED entities.
// Authenticates as a user inside the application whose tenant matches DEFAULT_TENANT,
// so the JSON upsert preprocessor accepts entity bodies tagged with that tenant.
const appAxiosInstance = axios.create({
                                          headers: {
                                              'login': APP_USER_EMAIL,
                                              'passcode': APP_USER_PASSWORD,
                                              'authScopeType': 'APPLICATION',
                                              'authScopeId': applicationId,
                                              'Content-Type': 'application/json'
                                          }
                                      })

describe('End To End Tests', () => {
    let context: LocalTestContext = { personWithTenantStructure: null! as EntityDefinition }
    let baseUrl: string

    beforeAll(async () => {
        await initKinoticClient()

        context.personWithTenantStructure = await createPersonEntityDefinitionIfNotExist(TEST_ORG_ID, applicationId, projectName, true)
        expect(context.personWithTenantStructure).toBeDefined()

        const { namedQueriesDefinition } = await createSchema(TEST_ORG_ID, applicationId, context.personWithTenantStructure.projectId, 'PersonWithTenant')
        const namedQueriesService = Kinotic.namedQueriesDefinitions
        await namedQueriesService.save(namedQueriesDefinition)

        // Provision an APPLICATION-scoped user that the SHARED-entity CRUD tests will authenticate
        // as. The user lives inside the application created above and is bound to DEFAULT_TENANT.
        const existing = await Kinotic.iamUsers.findByEmailAndScope(APP_USER_EMAIL, 'APPLICATION', applicationId)
        if (existing == null) {
            const appUser = new IamUser()
            appUser.email = APP_USER_EMAIL
            appUser.displayName = 'Application Test User'
            appUser.authType = AuthType.LOCAL
            appUser.authScopeType = 'APPLICATION'
            appUser.authScopeId = applicationId
            appUser.tenantId = DEFAULT_TENANT
            await Kinotic.iamUsers.createUser(appUser, APP_USER_PASSWORD)
        }

        // @ts-ignore
        const host = inject('KINOTIC_HOST') as string
        // @ts-ignore
        const port = inject('KINOTIC_OPENAPI_PORT') as string
        baseUrl = `http://${host}:${port}`
    }, 300000)

    afterAll(async () => {
        await shutdownKinoticClient()
    }, 60000)

    describe('OpenAPI Client', () => {

        it('OpenApi Schema loads', async () => {
            const schemaUrl = `${baseUrl}/api-docs/openapi.admin/openapi.json`
            const schema = await loadOpenAPISchema(schemaUrl)

            expect(schema).toBeDefined()
            expect(schema.openapi).toBe('3.0.1')
            expect(schema.info?.title).toBe('openapi.admin API')
        })

        describe('PersonWithTenant CRUD Operations', () => {

            it('Can create a person', async () => {
                const testPerson = generatePerson(DEFAULT_TENANT)
                const response = await appAxiosInstance.post<PersonWithTenant>(
                    `${baseUrl}/api/openapi.admin/personwithtenant`,
                    testPerson
                )

                expect(response.status).toBe(200)
                expect(response.data.id).toBeDefined()
                expect(response.data.firstName).toBe(testPerson.firstName)
                expect(response.data.tenantId).toBe(DEFAULT_TENANT)
            })

            it('Can get person by ID', async () => {
                // Create a person first
                const testPerson = generatePerson(DEFAULT_TENANT)
                const createResponse = await appAxiosInstance.post<PersonWithTenant>(
                    `${baseUrl}/api/openapi.admin/personwithtenant`,
                    testPerson
                )
                const createdId = createResponse.data.id!

                const response = await appAxiosInstance.get<PersonWithTenant>(
                    `${baseUrl}/api/openapi.admin/personwithtenant/${createdId}`
                )

                expect(response.status).toBe(200)
                expect(response.data.id).toBe(createdId)
                expect(response.data.firstName).toBe(testPerson.firstName)
                expect(response.data.lastName).toBe(testPerson.lastName)
            })

            // FIXME: These tests only fail in Github Actions
            // it('Can search for person', async () => {
            //     // Create a person to search for
            //     const testPerson = generatePerson(DEFAULT_TENANT)
            //     await axiosInstance.post<PersonWithTenant>(
            //         `${baseUrl}/api/openapi.admin/personwithtenant`,
            //         testPerson
            //     )
            //
            //     const testPerson2 = generatePerson(DEFAULT_TENANT)
            //     await axiosInstance.post<PersonWithTenant>(
            //         `${baseUrl}/api/openapi.admin/personwithtenant`,
            //         testPerson2
            //     )
            //
            //     await syncIndex(axiosInstance, baseUrl)
            //
            //     const searchResponse = await axiosInstance.post<{
            //         content: PersonWithTenant[];
            //         totalElements: number;
            //         cursor?: string;
            //     }>(
            //         `${baseUrl}/api/openapi.admin/personwithtenant/search`,
            //         `firstName:"${testPerson.firstName}"`,
            //         {
            //             headers: {
            //                 'Content-Type': 'text/plain' // Override default application/json
            //             },
            //             params : {
            //                 size: 10,
            //                 sort: 'lastName'
            //             }
            //         }
            //     )
            //
            //     expect(searchResponse.status).toBe(200)
            //     expect(searchResponse.data.content.length).toBeGreaterThan(0)
            //     expect(searchResponse.data.content.some(
            //         p => p.firstName === testPerson.firstName && p.lastName === testPerson.lastName
            //     )).toBe(true)
            // })
            //
            // it('Can count persons', async () => {
            //     // Ensure there's at least one person
            //     const testPerson = generatePerson(DEFAULT_TENANT)
            //     await axiosInstance.post<PersonWithTenant>(
            //         `${baseUrl}/api/openapi.admin/personwithtenant`,
            //         testPerson
            //     )
            //
            //     const testPerson2 = generatePerson(DEFAULT_TENANT)
            //     await axiosInstance.post<PersonWithTenant>(
            //         `${baseUrl}/api/openapi.admin/personwithtenant`,
            //         testPerson2
            //     )
            //
            //     await syncIndex(axiosInstance, baseUrl)
            //
            //     const countResponse = await axiosInstance.get<{ count: number }>(
            //         `${baseUrl}/api/openapi.admin/personwithtenant/count/all`
            //     )
            //
            //     expect(countResponse.status).toBe(200)
            //     expect(countResponse.data.count).toBeGreaterThan(0)
            // })

            it('Can delete person', async () => {
                // Create a person to delete
                const testPerson = generatePerson(DEFAULT_TENANT)
                const createResponse = await appAxiosInstance.post<PersonWithTenant>(
                    `${baseUrl}/api/openapi.admin/personwithtenant`,
                    testPerson
                )
                const createdId = createResponse.data.id!

                await syncIndex(appAxiosInstance, baseUrl)

                // Delete the person
                const deleteResponse = await appAxiosInstance.delete(
                    `${baseUrl}/api/openapi.admin/personwithtenant/${createdId}`
                )
                expect(deleteResponse.status).toBe(200) // Confirm delete request succeeds

                await syncIndex(appAxiosInstance, baseUrl)

                // Verify deletion by attempting to look it up (expecting 404)
                await expect(appAxiosInstance.get<PersonWithTenant>(
                    `${baseUrl}/api/openapi.admin/personwithtenant/${createdId}`
                )).rejects.toHaveProperty('response.status', 404)
            }, 600000)

        })

        describe('Multitenant Selection Operations', () => {

            // Helper to generate unique tenant IDs per test
            const generateUniqueTenants = (): [string, string, string] => {
                const tenantA = `tenantA-${faker.string.uuid()}`
                const tenantB = `tenantB-${faker.string.uuid()}`
                const tenantC = `tenantC-${faker.string.uuid()}`
                return [tenantA, tenantB, tenantC]
            }

            it('Can bulk save people across multiple tenants and retrieve them selectively', async () => {
                const [tenantA, tenantB, tenantC] = generateUniqueTenants()
                const tenants = [tenantA, tenantB, tenantC]
                const people = generatePeopleForTenants(tenants, 4)
                await bulkSavePeople(axiosInstance, baseUrl, people, tenantA)

                const query = buildFirstNameQuery(people)

                const tenantAData = await searchWithTenants(axiosInstance, baseUrl, query, [tenantA])
                expect(tenantAData.content.length).toBe(4)
                expect(tenantAData.content.every(p => p.tenantId === tenantA)).toBe(true)

                const tenantABData = await searchWithTenants(axiosInstance, baseUrl, query, [tenantA, tenantB])
                expect(tenantABData.content.length).toBe(8)
                expect(tenantABData.content.every(p => [tenantA, tenantB].includes(p.tenantId))).toBe(true)
            })

            it('Can count people across specific tenants', async () => {
                const [tenantA, tenantB, tenantC] = generateUniqueTenants()
                const tenants = [tenantA, tenantB, tenantC]
                const people = generatePeopleForTenants(tenants, 4)
                await bulkSavePeople(axiosInstance, baseUrl, people, tenantA)

                const query = buildFirstNameQuery(people)

                const tenantACount = await countWithTenants(axiosInstance, baseUrl, query, [tenantA])
                expect(tenantACount).toBe(4)

                const tenantACCount = await countWithTenants(axiosInstance, baseUrl, query, [tenantA, tenantC])
                expect(tenantACCount).toBe(8)
            })

            it('Can find people by IDs across specific tenants', async () => {
                const [tenantA, tenantB, tenantC] = generateUniqueTenants()
                const tenants = [tenantA, tenantB, tenantC]
                const people = generatePeopleForTenants(tenants, 4)
                await bulkSavePeople(axiosInstance, baseUrl, people, tenantA) // Includes sync

                const query = buildFirstNameQuery(people)
                const searchResponse = await searchWithTenants(axiosInstance, baseUrl, query, tenants, 20)
                expect(searchResponse.content.length).toBe(12)

                people.forEach((person, index) => {
                    const match = searchResponse.content.find(
                        p => p.firstName === person.firstName && p.lastName === person.lastName
                    )
                    if (match) people[index].id = match.id!
                })

                const tenantBIds = people
                    .filter(p => p.tenantId === tenantB)
                    .map(p => ({ entityId: p.id!, tenantId: p.tenantId }))

                const findTenantB = await axiosInstance.post<PersonWithTenant[]>(
                    `${baseUrl}/admin/api/openapi.admin/personwithtenant/find/by-ids`,
                    tenantBIds
                )

                expect(findTenantB.status).toBe(200)
                expect(findTenantB.data.length).toBe(4)
                expect(findTenantB.data.every(p => p.tenantId === tenantB)).toBe(true)

                const tenantACIds = people
                    .filter(p => [tenantA, tenantC].includes(p.tenantId))
                    .map(p => ({ entityId: p.id!, tenantId: p.tenantId }))
                const findTenantAC = await axiosInstance.post<PersonWithTenant[]>(
                    `${baseUrl}/admin/api/openapi.admin/personwithtenant/find/by-ids`,
                    tenantACIds
                )
                expect(findTenantAC.status).toBe(200)
                expect(findTenantAC.data.length).toBe(8)
                expect(findTenantAC.data.every(p => [tenantA, tenantC].includes(p.tenantId))).toBe(true)
            })

            it('Can delete people across specific tenants', async () => {
                const [tenantA, tenantB, tenantC] = generateUniqueTenants()
                const tenants = [tenantA, tenantB, tenantC]
                const people = generatePeopleForTenants(tenants, 4)
                await bulkSavePeople(axiosInstance, baseUrl, people, tenantA)

                const query = buildFirstNameQuery(people)

                const deleteTenantB = await axiosInstance.post(
                    `${baseUrl}/admin/api/openapi.admin/personwithtenant/delete/by-query`,
                    {
                        query: query,
                        tenantSelection: [tenantB]
                    }
                )
                expect(deleteTenantB.status).toBe(200)
                await syncIndex(axiosInstance, baseUrl)

                const verifyB = await searchWithTenants(axiosInstance, baseUrl, query, [tenantB])
                expect(verifyB.content.length).toBe(0)

                const verifyAC = await searchWithTenants(axiosInstance, baseUrl, query, [tenantA, tenantC])
                expect(verifyAC.content.length).toBe(8)
            })
        })

    })
})