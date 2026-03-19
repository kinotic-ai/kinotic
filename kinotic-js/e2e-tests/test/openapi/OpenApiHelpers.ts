import {faker} from '@faker-js/faker';
import axios, {AxiosInstance, AxiosResponse} from 'axios';
import {expect} from 'vitest';
import {Address} from '../domain/Address.js';
import {PersonWithTenant} from '../domain/PersonWithTenant.js';
import {Pet} from '../domain/Pet.js';

// Basic interface for an OpenAPI schema (v3.x minimum)
interface BasicOpenAPISchema {
    openapi: string;
    info?: {
        title?: string;
        version?: string;
    };
    paths?: object;
    components?: object;
}

/**
 * Fetches and validates an OpenAPI schema from a given URL
 * @param url The endpoint URL serving the OpenAPI schema
 * @returns Promise resolving to the schema if valid, or throws an error if invalid
 */
export async function loadOpenAPISchema(url: string): Promise<BasicOpenAPISchema> {
    try {
        const response: AxiosResponse = await axios.get(url, {
            headers: {
                Accept: 'application/json, application/yaml'
            }
        });

        const schemaData: unknown = response.data;

        if (!isValidOpenAPISchema(schemaData)) {
            throw new Error('Response does not contain a valid OpenAPI schema');
        }

        return schemaData as BasicOpenAPISchema;
    } catch (error) {
        if (axios.isAxiosError(error)) {
            throw new Error(`Failed to fetch schema: ${error.message}`);
        }
        throw error;
    }
}

/**
 * Validates that the data conforms to a basic OpenAPI 3.x schema
 * @param data The data to validate
 * @returns boolean indicating if it's a valid OpenAPI schema
 */
function isValidOpenAPISchema(data: unknown): boolean {
    // Check if data is an object
    if (!data || typeof data !== 'object') {
        return false
    }

    const schema = data as Record<string, unknown>

    // Check for required openapi version property (must be 3.x)
    if (typeof schema.openapi !== 'string' || !schema.openapi.startsWith('3.')) {
        return false
    }

    // Check for typical OpenAPI structure: at least paths or components must be present
    // Require at least one structural element (paths or components)
    // info is optional but often present in a useful spec
    const hasPaths = schema.paths !== undefined && typeof schema.paths === 'object'
    const hasComponents = schema.components !== undefined && typeof schema.components === 'object'

    return hasPaths || hasComponents
}

/**
 * Generates a single person with fake data for a given tenant
 * @param tenantId The tenant ID to assign to the person
 * @returns A PersonWithTenant object
 */
export function generatePerson(tenantId: string): PersonWithTenant {
    const person = new PersonWithTenant();
    person.firstName = faker.person.firstName();
    person.lastName = faker.person.lastName();
    person.age = faker.number.int({ min: 18, max: 80 });
    person.address = Object.assign(new Address(), {
        street: faker.location.streetAddress(),
        city: faker.location.city(),
        state: faker.location.state({ abbreviated: true }),
        zip: faker.location.zipCode()
    });
    person.myPet = {
        type: faker.helpers.arrayElement(['Dog', 'Cat']),
        name: faker.animal[faker.helpers.arrayElement(['dog', 'cat'])](),
        age: faker.number.int({ min: 1, max: 15 })
    } as Pet;
    person.tenantId = tenantId;
    return person;
}

/**
 * Syncs the index to make recent updates immediately available
 * @param axiosInstance Axios instance with auth headers
 * @param baseUrl Base URL of the API
 */
export async function syncIndex(axiosInstance: AxiosInstance, baseUrl: string): Promise<void> {
    const syncResponse = await axiosInstance.get(`${baseUrl}/api/openapi.admin/personwithtenant/util/sync`);
    expect(syncResponse.status).toBe(200);
}

/**
 * Bulk saves people and syncs the index
 * @param axiosInstance Axios instance with auth headers
 * @param baseUrl Base URL of the API
 * @param people Array of people to save
 * @param actingTenant Tenant ID for the request header
 */
export async function bulkSavePeople(
    axiosInstance: AxiosInstance,
    baseUrl: string,
    people: PersonWithTenant[],
    actingTenant: string
): Promise<void> {
    const bulkSaveResponse = await axiosInstance.post(
        `${baseUrl}/api/openapi.admin/personwithtenant/bulk`,
        people,
        { headers: { tenantId: actingTenant } }
    )
    expect(bulkSaveResponse.status).toBe(200)
    await syncIndex(axiosInstance, baseUrl) // Sync is here
}

/**
 * Generates multiple people across specified tenants
 * @param tenants Array of tenant IDs
 * @param countPerTenant Number of people per tenant
 * @returns Array of generated PersonWithTenant objects
 */
export function generatePeopleForTenants(
    tenants: string[],
    countPerTenant: number
): PersonWithTenant[] {
    const people: PersonWithTenant[] = [];
    for (const tenant of tenants) {
        for (let i = 0; i < countPerTenant; i++) {
            people.push(generatePerson(tenant));
        }
    }
    return people;
}

/**
 * Builds a Lucene query string for first names
 * @param people Array of people to include in the query
 * @returns Lucene query string
 */
export function buildFirstNameQuery(people: PersonWithTenant[]): string {
    return `firstName:(${people.map(p => `"${p.firstName}"`).join(' ')})`;
}

/**
 * Searches for people with tenant selection
 * @param axiosInstance Axios instance with auth headers
 * @param baseUrl Base URL of the API
 * @param query Lucene query string
 * @param tenantSelection Array of tenant IDs to filter by
 * @param size Maximum number of results (default: 20)
 * @returns Search response data
 */
export async function searchWithTenants(
    axiosInstance: AxiosInstance,
    baseUrl: string,
    query: string,
    tenantSelection: string[],
    size: number = 20
): Promise<{ content: PersonWithTenant[]; totalElements: number; cursor?: string }> {
    const response = await axiosInstance.post<{
        content: PersonWithTenant[];
        totalElements: number;
        cursor?: string;
    }>(
        `${baseUrl}/admin/api/openapi.admin/personwithtenant/search`,
        { query, tenantSelection },
        {
            headers: { 'Content-Type': 'application/json' },
            params: { size }
        }
    );
    expect(response.status).toBe(200);
    return response.data;
}

/**
 * Counts people with tenant selection
 * @param axiosInstance Axios instance with auth headers
 * @param baseUrl Base URL of the API
 * @param query Lucene query string
 * @param tenantSelection Array of tenant IDs to filter by
 * @returns Count of matching people
 */
export async function countWithTenants(
    axiosInstance: AxiosInstance,
    baseUrl: string,
    query: string,
    tenantSelection: string[]
): Promise<number> {
    const response = await axiosInstance.post<{ count: number }>(
        `${baseUrl}/admin/api/openapi.admin/personwithtenant/count/by-query`,
        { query, tenantSelection },
        { headers: { 'Content-Type': 'application/json' } }
    );
    expect(response.status).toBe(200);
    return response.data.count;
}