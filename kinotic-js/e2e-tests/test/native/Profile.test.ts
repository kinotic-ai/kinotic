import {Kinotic} from '@kinotic-ai/core'
import {OidcConfigurationType, OidcProviderType, PASSWORD_SIGN_IN_METHOD_ID, AuthType} from '@kinotic-ai/management-api'
import * as allure from 'allure-js-commons'
import {afterAll, beforeAll, describe, expect, it} from 'vitest'
import {E2E_ORG_USER_EMAIL, initKinoticClient, shutdownKinoticClient} from '../TestHelpers.js'

/**
 * The signed-in organization user's own profile over STOMP: who it is, how it signs in, which providers it may connect,
 * and which organizations it belongs to. Connected as the e2e organization user.
 */
describe('Profile', () => {

    beforeAll(async () => {
        await allure.suite('e2e-tests/native')
        await allure.subSuite('Profile')
        await initKinoticClient()
    }, 300000)

    afterAll(async () => {
        await shutdownKinoticClient()
    }, 60000)

    it('describes the signed-in organization user', async () => {
        const profile = await Kinotic.profile.findMyProfile()
        expect(profile.email).toBe(E2E_ORG_USER_EMAIL)
        expect(profile.id).toBeTruthy()
    })

    it('lists the user\'s sign-in methods, its password among them', async () => {
        const methods = await Kinotic.profile.findMySignInMethods()
        expect(methods.some(m => m.id === PASSWORD_SIGN_IN_METHOD_ID && m.type === AuthType.PASSWORD)).toBe(true)
    })

    it('offers the system\'s GitHub registration to connect', async () => {
        const providers = await Kinotic.profile.findConnectableProviders()
        const github = providers.find(p => p.configurationId === 'github-platform')
        expect(github).toBeDefined()
        expect(github!.provider).toBe(OidcProviderType.GITHUB)
        expect(github!.type).toBe(OidcConfigurationType.ORG_SIGNUP)
    })

    it('renames the user and reads the name back', async () => {
        const original = (await Kinotic.profile.findMyProfile()).displayName
        try {
            const renamed = await Kinotic.profile.updateDisplayName('Kinotic Test (renamed)')
            expect(renamed.displayName).toBe('Kinotic Test (renamed)')
            expect((await Kinotic.profile.findMyProfile()).displayName).toBe('Kinotic Test (renamed)')
        } finally {
            await Kinotic.profile.updateDisplayName(original ?? 'Kinotic Test')
        }
    })
})
