import {describe, expect, inject, it} from 'vitest'

// The test server's security service admits these headers, as it admits a STOMP CONNECT carrying them
const AUTH_HEADERS = {clientId: 'kinotic@kinotic.local', clientSecret: 'kinotic', organizationId: 'test-org'}

function restUrl(path: string): string {
    // @ts-ignore
    return `http://${inject('KINOTIC_HOST')}:${inject('KINOTIC_PORT')}/api/srv/${path}`
}

function post(path: string, body: string, contentType = 'application/json'): Promise<Response> {
    return fetch(restUrl(path), {method: 'POST', headers: {...AUTH_HEADERS, 'content-type': contentType}, body})
}

describe('Kinotic JS', () => {
    describe('packages/core', () => {
        describe('REST RPC route', () => {

            it('should answer a function with its result', async () => {
                const response = await post('management-api~org.kinotic.testserver.ITestService/testMethodWithString', '["Bob"]')
                expect(response.status).toBe(200)
                await expect(response.json()).resolves.toBe('Hello Bob')
            })

            it('should call the version the path names', async () => {
                const response = await post('management-api~org.kinotic.testserver.ITestService/testMethodWithString%231.0.0', '["Bob"]')
                expect(response.status).toBe(200)
                await expect(response.json()).resolves.toBe('Hello Bob')
            })

            it('should answer 404 for an address nothing serves', async () => {
                const response = await post('management-api~com.namespace.NonExistentService/probablyNotHome', '[]')
                expect(response.status).toBe(404)
            })

            it('should answer 500 with the error when the function fails', async () => {
                const response = await post('management-api~org.kinotic.testserver.ITestService/testMissingMethod', '[]')
                expect(response.status).toBe(500)
                await expect(response.json()).resolves.toEqual({error: 'No method could be resolved for methodId /testMissingMethod'})
            })

            it('should refuse a body that is not JSON arguments', async () => {
                const response = await post('management-api~org.kinotic.testserver.ITestService/testMethodWithString', 'Bob', 'text/plain')
                expect(response.status).toBe(415)
            })

            it('should refuse a path that names no function', async () => {
                const response = await post('', '[]')
                expect(response.status).toBe(400)
            })
        })
    })
})
