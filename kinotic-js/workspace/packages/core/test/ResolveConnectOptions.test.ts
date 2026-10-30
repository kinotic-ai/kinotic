import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { Util } from '../src/internal/api/Util'

const SERVER_ENV = ['KINOTIC_SERVER_HOST', 'KINOTIC_SERVER_PORT', 'KINOTIC_SERVER_USE_SSL'] as const

describe('resolveConnectOptions', () => {

    const saved: Partial<Record<typeof SERVER_ENV[number], string>> = {}

    beforeEach(() => {
        for (const name of SERVER_ENV) {
            saved[name] = process.env[name]
            delete process.env[name]
        }
    })

    afterEach(() => {
        for (const name of SERVER_ENV) {
            if (saved[name] === undefined) {
                delete process.env[name]
            } else {
                process.env[name] = saved[name]
            }
        }
    })

    it('reaches a named host over TLS on the scheme default port', () => {
        expect(Util.resolveConnectOptions({ server: { host: 'kinotic.example' } }).server)
            .toEqual({ host: 'kinotic.example', port: null, useSSL: true })
    })

    it('reaches a loopback host in plaintext on the gateway port', () => {
        for (const host of ['localhost', '127.0.0.1', '::1', '[::1]', 'LOCALHOST']) {
            expect(Util.resolveConnectOptions({ server: { host } }).server).toEqual({ host, port: 58503, useSSL: false })
        }
    })

    it('keeps an explicit useSSL and derives the port from it', () => {
        expect(Util.resolveConnectOptions({ server: { host: 'kinotic.example', useSSL: false } }).server)
            .toEqual({ host: 'kinotic.example', port: 58503, useSSL: false })
        expect(Util.resolveConnectOptions({ server: { host: 'localhost', useSSL: true } }).server)
            .toEqual({ host: 'localhost', port: null, useSSL: true })
    })

    it('reads the environment before applying the loopback default', () => {
        process.env.KINOTIC_SERVER_HOST = 'kinotic.example'
        process.env.KINOTIC_SERVER_USE_SSL = 'false'
        expect(Util.resolveConnectOptions().server).toEqual({ host: 'kinotic.example', port: 58503, useSSL: false })

        process.env.KINOTIC_SERVER_PORT = '58505'
        delete process.env.KINOTIC_SERVER_USE_SSL
        expect(Util.resolveConnectOptions().server).toEqual({ host: 'kinotic.example', port: 58505, useSSL: true })
    })

    it('connects to the cloud over TLS when nothing names a server', () => {
        expect(Util.resolveConnectOptions().server).toEqual({ host: 'api.kinotic.ai', port: null, useSSL: true })
    })
})
