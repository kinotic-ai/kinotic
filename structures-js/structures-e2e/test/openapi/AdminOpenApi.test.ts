import {Structure, Structures} from '@kinotic/structures-api'
import * as allure from 'allure-js-commons'
import axios from 'axios'
import {afterAll, beforeAll, describe, expect, inject, it} from 'vitest'
import {PersonWithTenant} from '../domain/PersonWithTenant.js'
import {createPersonStructureIfNotExist, createSchema, initContinuumClient, shutdownContinuumClient} from '../TestHelpers.js'
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
    personWithTenantStructure: Structure
}

const applicationId = 'openapi.admin'
const projectName = 'TestProject'
const BASE_AUTH = 'Basic YWRtaW46c3RydWN0dXJlcw=='
const DEFAULT_TENANT = 'mindignited'

const axiosInstance = axios.create({
                                       headers: {
                                           'Authorization': BASE_AUTH,
                                           'Content-Type': 'application/json'
                                       }
                                   })

describe('End To End Tests', () => {
    let context: LocalTestContext = { personWithTenantStructure: null! }
    let baseUrl: string

    beforeAll(async () => {
        await initContinuumClient()

        context.personWithTenantStructure = await createPersonStructureIfNotExist(applicationId, projectName, true)
        expect(context.personWithTenantStructure).toBeDefined()

        const { namedQueriesDefinition } = await createSchema(applicationId, context.personWithTenantStructure.projectId, 'PersonWithTenant')
        const namedQueriesService = Structures.getNamedQueriesService()
        await namedQueriesService.save(namedQueriesDefinition)

        // @ts-ignore
        const host = inject('STRUCTURES_HOST') as string
        // @ts-ignore
        const port = inject('STRUCTURES_OPENAPI_PORT') as string
        baseUrl = `http://${host}:${port}`
    }, 300000)

    afterAll(async () => {
        await shutdownContinuumClient()
    }, 60000)

    describe('OpenAPI Client', () => {

        it('OpenApi Schema loads', async () => {
            const schemaUrl = `${baseUrl}/api-docs/openapi.admin/openapi.json`
            const schema = await loadOpenAPISchema(schemaUrl)

            expect(schema).toBeDefined()
            expect(schema.openapi).toBe('3.0.1')
            expect(schema.info?.title).toBe('openapi.admin Structures API')
        })

        describe('PersonWithTenant CRUD Operations', () => {

            it('Can create a person', async () => {
                const testPerson = generatePerson(DEFAULT_TENANT)
                const response = await axiosInstance.post<PersonWithTenant>(
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
                const createResponse = await axiosInstance.post<PersonWithTenant>(
                    `${baseUrl}/api/openapi.admin/personwithtenant`,
                    testPerson
                )
                const createdId = createResponse.data.id!

                const response = await axiosInstance.get<PersonWithTenant>(
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
                const createResponse = await axiosInstance.post<PersonWithTenant>(
                    `${baseUrl}/api/openapi.admin/personwithtenant`,
                    testPerson
                )
                const createdId = createResponse.data.id!

                await syncIndex(axiosInstance, baseUrl)

                // Delete the person
                const deleteResponse = await axiosInstance.delete(
                    `${baseUrl}/api/openapi.admin/personwithtenant/${createdId}`
                )
                expect(deleteResponse.status).toBe(200) // Confirm delete request succeeds

                await syncIndex(axiosInstance, baseUrl)

                // Verify deletion by attempting to look it up (expecting 404)
                await expect(axiosInstance.get<PersonWithTenant>(
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