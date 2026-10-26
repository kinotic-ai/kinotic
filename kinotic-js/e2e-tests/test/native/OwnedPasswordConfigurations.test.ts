import {Kinotic, Pageable} from '@kinotic-ai/core'
import {type PasswordConfiguration, TenantManagement} from '@kinotic-ai/management-api'
import * as allure from 'allure-js-commons'
import {afterAll, beforeAll, describe, expect, it} from 'vitest'
import {E2E_ORGANIZATION_ID, initKinoticClient, shutdownKinoticClient} from '../TestHelpers.js'

/**
 * The application whose login this suite gives an email and a password; created by the suite, as no fixture is its own,
 * with tenants the system manages, which password sign-up needs.
 */
const APPLICATION_ID = 'e2e-owned-password'

/**
 * An organization's own password configurations over STOMP, as one of its members: an application's email and password
 * from being saved, under the id its scope gives it, through being disabled and deleted, and the saves refused along
 * the way. The organization's own configuration, which the fixtures seed for the other suites' logins, is left as it is.
 */
describe('Owned password configurations', () => {

    beforeAll(async () => {
        await allure.suite('e2e-tests/native')
        await allure.subSuite('OwnedPasswordConfigurations')
        await initKinoticClient()
        await Kinotic.applications.createApplicationIfNotExist(APPLICATION_ID, 'e2e fixture application for the owned password configuration test',
                                                               TenantManagement.SYSTEM)
    }, 300000)

    afterAll(async () => {
        await shutdownKinoticClient()
    }, 60000)

    function applicationPassword(): PasswordConfiguration {
        return {
            // derived from the scope on save, whatever is sent
            id: 'e2e-any-id',
            organizationId: null,
            applicationId: APPLICATION_ID,
            tenantId: null,
            enabled: true,
            createUserOnSignIn: true,
            created: null,
            updated: null
        }
    }

    async function listedIds(): Promise<string[]> {
        const page = await Kinotic.ownedPasswordConfigurations.findAll(Pageable.create(0, 1000))
        return (page.content ?? []).map(configuration => configuration.id!)
    }

    it('saves an application\'s email and password under the id of its scope, and lists it', async () => {
        const saved = await Kinotic.ownedPasswordConfigurations.save(applicationPassword())
        expect(saved.id).toBe(`${E2E_ORGANIZATION_ID}:${APPLICATION_ID}`)
        expect(saved.organizationId).toBe(E2E_ORGANIZATION_ID)
        expect(saved.createUserOnSignIn).toBe(true)
        expect(await listedIds()).toContain(saved.id)
        // the organization's own, which signs the other suites' members in
        expect(await listedIds()).toContain(E2E_ORGANIZATION_ID)
    })

    it('refuses sign-up at the organization\'s login, and an application that is not the organization\'s', async () => {
        await expect(Kinotic.ownedPasswordConfigurations.save({...applicationPassword(), applicationId: null}))
            .rejects.toThrow()
        await expect(Kinotic.ownedPasswordConfigurations.save({...applicationPassword(), applicationId: 'e2e-no-such-application', createUserOnSignIn: false}))
            .rejects.toThrow()
    })

    it('disables the application\'s email and password, and deletes it', async () => {
        const disabled = await Kinotic.ownedPasswordConfigurations.save({...applicationPassword(), enabled: false})
        expect(disabled.enabled).toBe(false)

        await Kinotic.ownedPasswordConfigurations.deleteById(disabled.id!)
        expect(await listedIds()).not.toContain(disabled.id)
    })
})
