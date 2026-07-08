import {Kinotic} from '@kinotic-ai/core'
import * as allure from 'allure-js-commons'
import {afterAll, beforeAll, describe, expect, it} from 'vitest'
import {initKinoticClient, shutdownKinoticClient} from '../TestHelpers.js'

describe('Kinotic JS', () => {

    beforeAll(async () => {
        await allure.suite('e2e-tests/native')
        await allure.subSuite('ApplicationService')
        await initKinoticClient()
    }, 300000)

    afterAll(async () => {
        await shutdownKinoticClient()
    }, 60000)

    it('derives the application id from the slugified name', async () => {
        const application = await Kinotic.applications.createApplicationIfNotExist('E2E Slug. Test!', 'Application id derivation')
        try {
            expect(application.id).toBe('e2e_slug_test')
        } finally {
            await Kinotic.applications.deleteById(application.id)
        }
    })

    it('returns the existing application when the slugified name matches', async () => {
        const first = await Kinotic.applications.createApplicationIfNotExist('E2E Idempotent App', 'first')
        try {
            // The slug itself and the punctuated name mint the same id
            const second = await Kinotic.applications.createApplicationIfNotExist('e2e_idempotent_app', 'second')
            expect(second.id).toBe(first.id)
            expect(second.description).toBe('first')
        } finally {
            await Kinotic.applications.deleteById(first.id)
        }
    })

})
