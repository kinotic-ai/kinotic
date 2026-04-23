import {EntityDefinition} from '@kinotic-ai/os-api'
import * as allure from 'allure-js-commons'
import {afterAll, beforeAll, describe, expect, inject, it} from 'vitest'
import {createVehicleEntityDefinitionIfNotExist, initKinoticClient, shutdownKinoticClient} from '../TestHelpers.js'
import {loadOpenAPISchema} from './OpenApiHelpers.js'


interface LocalTestContext {
    personStructure: EntityDefinition
    personWithTenantStructure: EntityDefinition
    vehicleStructure: EntityDefinition
}

const TEST_ORG_ID = 'kinotic-test'
const applicationId = 'openapi.versioned'
const projectName = 'TestProject'

describe('Versioned OpenApi Tests', () => {

    let context: LocalTestContext = {} as LocalTestContext

    beforeAll(async () => {
        await allure.parentSuite('End To End Tests')
        await initKinoticClient()

        context.vehicleStructure = await createVehicleEntityDefinitionIfNotExist(TEST_ORG_ID, applicationId, projectName)
        expect(context.vehicleStructure).toBeDefined()

    }, 300000)

    afterAll(async () => {
        // await expect(deleteStructure(context.vehicleStructure.id as string)).resolves.toBeUndefined()
        await shutdownKinoticClient()
    }, 60000)


    it<LocalTestContext>(
        'OpenApi Schema loads',
        async () => {
            // @ts-ignore
            const host = inject('KINOTIC_HOST')
            // @ts-ignore
            const port = inject('KINOTIC_OPENAPI_PORT')
            const schemaUrl = `http://${host}:${port}/api-docs/openapi.versioned/openapi.json`

            const schema = await loadOpenAPISchema(schemaUrl)

            expect(schema).toBeDefined()
            expect(schema.openapi).toBe('3.0.1')
            expect(schema.info?.title).toBe('openapi.versioned API')
        }
    )

})