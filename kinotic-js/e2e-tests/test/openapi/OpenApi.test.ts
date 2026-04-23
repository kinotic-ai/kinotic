import {EntityDefinition} from '@kinotic-ai/os-api'
import * as allure from 'allure-js-commons'
import {afterAll, beforeAll, describe, expect, inject, it} from 'vitest'
import {createPersonEntityDefinitionIfNotExist, initKinoticClient, shutdownKinoticClient} from '../TestHelpers.js'
import {loadOpenAPISchema} from './OpenApiHelpers.js'


interface LocalTestContext {
    personStructure: EntityDefinition
    personWithTenantStructure: EntityDefinition
    vehicleStructure: EntityDefinition
}

const TEST_ORG_ID = 'kinotic-test'
const applicationId = 'openapi'
const projectName = 'TestProject'

describe('OpenApi Tests', () => {

    let context: LocalTestContext = {} as LocalTestContext

    beforeAll(async () => {
        await allure.parentSuite('End To End Tests')
        await initKinoticClient()

        context.personStructure = await createPersonEntityDefinitionIfNotExist(TEST_ORG_ID, applicationId, projectName)
        expect(context.personStructure).toBeDefined()
        context.personWithTenantStructure = await createPersonEntityDefinitionIfNotExist(TEST_ORG_ID, applicationId, projectName, true)

    }, 300000)

    afterAll(async () => {
        // await expect(deleteStructure(context.personStructure.id as string)).resolves.toBeUndefined()
        await shutdownKinoticClient()
    }, 60000)


    it<LocalTestContext>(
        'OpenApi Schema loads',
        async () => {
            // @ts-ignore
            const host = inject('KINOTIC_HOST')
            // @ts-ignore
            const port = inject('KINOTIC_OPENAPI_PORT')
            const schemaUrl = `http://${host}:${port}/api-docs/openapi/openapi.json`

            const schema = await loadOpenAPISchema(schemaUrl)

            expect(schema).toBeDefined()
            expect(schema.openapi).toBe('3.0.1')
            expect(schema.info?.title).toBe('openapi API')
        }
    )

})