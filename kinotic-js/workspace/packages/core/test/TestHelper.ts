import {ConnectedInfo, type ConnectOptions, type CredentialsResolver, SessionKeepAliveMode} from '../src'
import {ensureNodeWebSocket} from '../src/node'
import { expect, inject } from 'vitest'
import * as fs from 'fs'
import * as path from 'path'

/**
 * Credentials passed as WebSocket upgrade headers; the gateway's
 * KinoticSecurityService authenticates the participant from these before
 * the STOMP CONNECT frame is processed.
 */
export interface AuthHeaders {
    clientId: string
    clientSecret: string
    organizationId?: string
    applicationId?: string
}

const DEFAULT_AUTH_HEADERS: AuthHeaders = {
    clientId: 'kinotic@kinotic.local',
    clientSecret: 'kinotic',
    organizationId: 'kinotic-test'
}

/**
 * Returns the Kinotic Docker image string with version from gradle.properties
 */
export function getKinoticDockerImage(): string {
    const gradlePropsPath = path.resolve(__dirname, '../../../../../gradle.properties')
    const content = fs.readFileSync(gradlePropsPath, 'utf-8')
    const versionMatch = content.match(/kinoticVersion=(.+)/)
    const version = versionMatch?.[1]
    if (!version) {
        throw new Error('Could not find kinoticVersion in gradle.properties')
    }
    return `kinoticai/kinotic-server:${version.trim()}`
}

export const KINOTIC_DOCKER_IMAGE: string = getKinoticDockerImage()

/**
 * Logs the failure of a promise and then rethrows the error
 * @param promise to log failure of
 * @param message to log
 */
export async function logFailure<T>(promise: Promise<T>, message: string): Promise<T> {
    try {
        return await promise
    } catch (e) {
        console.error(message, e)
        throw e
    }
}

export function validateConnectedInfo(connectedInfo: ConnectedInfo, roles?: string[]): void {
    expect(connectedInfo).toBeDefined()
    expect(connectedInfo.participant.id).toBeDefined()
    expect(connectedInfo.participant.roles).toBeDefined()
    expect(connectedInfo.participant.roles.length).toBe(1)
    if (roles) {
        expect(connectedInfo.participant.roles).toEqual(roles)
    } else {
        expect(connectedInfo.participant.roles[0]).toBe('ADMIN')
    }
}

/**
 * A {@link CredentialsResolver} producing the given auth headers merged over the default
 * kinotic-test credentials. The supplier form is consulted on every (re)connect.
 */
export function testCredentials(authHeaders?: Partial<AuthHeaders> | (() => Promise<Partial<AuthHeaders>>)): CredentialsResolver {
    return {
        name: 'TestCredentialsResolver',
        async resolve() {
            const overrides = typeof authHeaders === 'function' ? await authHeaders() : (authHeaders ?? {})
            const merged = {...DEFAULT_AUTH_HEADERS, ...overrides}
            const headers: Record<string, string> = {clientId: merged.clientId, clientSecret: merged.clientSecret}
            if (merged.organizationId != null) headers.organizationId = merged.organizationId
            if (merged.applicationId != null) headers.applicationId = merged.applicationId
            return {authHeaders: headers}
        }
    }
}

export function createConnectOptions(options: {
    sessionKeepAlive?: SessionKeepAliveMode
    authHeaders?: Partial<AuthHeaders> | (() => Promise<Partial<AuthHeaders>>)
} = {}): ConnectOptions {
    ensureNodeWebSocket()
    const { sessionKeepAlive = SessionKeepAliveMode.ACTIVITY, authHeaders } = options
    return {
        // @ts-ignore
        host: inject('KINOTIC_HOST'),
        // @ts-ignore
        port: inject('KINOTIC_PORT'),
        maxConnectionAttempts: 3,
        sessionKeepAlive,
        credentials: testCredentials(authHeaders)
    }
}
