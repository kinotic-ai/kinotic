import {Structure,} from '@mindignited/structures-api'
import * as allure from 'allure-js-commons'
import {afterAll, beforeAll, describe, expect, inject, it} from 'vitest'
import {createVehicleStructureIfNotExist, initContinuumClient, shutdownContinuumClient} from '../TestHelpers.js'
import {loadOpenAPISchema} from './OpenApiHelpers.js'


interface LocalTestContext {
    personStructure: Structure
    personWithTenantStructure: Structure
    vehicleStructure: Structure
}

const applicationId = 'openapi.versioned'
const projectName = 'TestProject'

describe('Versioned OpenApi Tests', () => {

    let context: LocalTestContext = {} as LocalTestContext

    beforeAll(async () => {
        await allure.parentSuite('End To End Tests')
        await initContinuumClient()

        context.vehicleStructure = await createVehicleStructureIfNotExist(applicationId, projectName)
        expect(context.vehicleStructure).toBeDefined()

    }, 300000)

    afterAll(async () => {
        // await expect(deleteStructure(context.vehicleStructure.id as string)).resolves.toBeUndefined()
        await shutdownContinuumClient()
    }, 60000)


    it<LocalTestContext>(
        'OpenApi Schema loads',
        async () => {
            // @ts-ignore
            const host = inject('STRUCTURES_HOST')
            // @ts-ignore
            const port = inject('STRUCTURES_OPENAPI_PORT')
            const schemaUrl = `http://${host}:${port}/api-docs/openapi.versioned/openapi.json`

            const schema = await loadOpenAPISchema(schemaUrl)

            expect(schema).toBeDefined()
            expect(schema.openapi).toBe('3.0.1')
            expect(schema.info?.title).toBe('openapi.versioned Structures API')
        }
    )

})