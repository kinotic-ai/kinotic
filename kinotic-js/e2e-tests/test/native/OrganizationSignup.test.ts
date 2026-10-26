import * as allure from 'allure-js-commons'
import {beforeAll, describe, expect, it} from 'vitest'
import {generateRandomString, managementServer, restBase} from '../TestHelpers.js'

/**
 * Signing up with an email and a password on a server that offers it and shows links in the UI in place of email, as the
 * e2e stack and a demonstration server do: the verification link comes back with the request, and the sign-up
 * completes there.
 */
describe('OrganizationSignup', () => {

    let baseUrl: string

    beforeAll(async () => {
        await allure.suite('e2e-tests/native')
        await allure.subSuite('OrganizationSignup')
        baseUrl = restBase(managementServer())
    }, 300000)

    function post(path: string, body: unknown): Promise<Response> {
        return fetch(`${baseUrl}${path}`, {method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify(body)})
    }

    it('offers signing up with a password where the deployment sets it', async () => {
        const res = await fetch(`${baseUrl}/api/auth/org/signup/methods`)
        expect(res.status).toBe(200)
        expect(await res.json()).toEqual({password: true})
    })

    it('hands back the verification link, again when asked again, and completes the sign-up there', async () => {
        const email = `e2e-signup-${generateRandomString(8).toLowerCase()}@example.test`
        const started = await post('/api/auth/org/signup', {email, displayName: 'Signs Up'})
        expect(started.status).toBe(200)
        const verificationUrl: string = (await started.json()).verificationUrl
        expect(verificationUrl).toMatch(/\/signup\/verify\?token=/)

        const again = await post('/api/auth/org/signup', {email, displayName: 'Signs Up'})
        expect((await again.json()).verificationUrl).toBe(verificationUrl)

        const completed = await post('/api/auth/org/signup/complete', {
            token: new URL(verificationUrl).searchParams.get('token'),
            orgName: `Signup ${generateRandomString(6)}`,
            password: 'e2e-signup-password-1'
        })
        expect(completed.status).toBe(204)
        expect(completed.headers.get('set-cookie')).toMatch(/session=/)
    })
})
