import {ConnectedInfo, ConnectionInfo, IWebSocket, SessionKeepAliveMode, WebSocketFactory} from '../src'
import { expect, inject } from 'vitest'
import { WebSocket } from 'ws'
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
    if (!versionMatch) {
        throw new Error('Could not find kinoticVersion in gradle.properties')
    }
    return `kinoticai/kinotic-server:${versionMatch[1].trim()}`
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

function buildWsUrl(host: string, port: number, useSSL: boolean = false): string {
    return `${useSSL ? 'wss' : 'ws'}://${host}:${port}/v1`
}

/**
 * Builds a webSocketFactory that attaches the given auth headers — merged
 * over the default kinotic-test credentials — to the WebSocket upgrade. The
 * async provider form lets callers refresh headers on every (re)connect.
 */
export function authedWebSocketFactory(host: string,
                                       port: number,
                                       authHeaders?: Partial<AuthHeaders> | (() => Promise<Partial<AuthHeaders>>),
                                       useSSL: boolean = false): WebSocketFactory {
    const wsUrl = buildWsUrl(host, port, useSSL)
    return async () => {
        const overrides = typeof authHeaders === 'function' ? await authHeaders() : (authHeaders ?? {})
        const headers: AuthHeaders = { ...DEFAULT_AUTH_HEADERS, ...overrides }
        return new WebSocket(wsUrl, { headers: headers as unknown as Record<string, string> }) as unknown as IWebSocket
    }
}

export function createConnectionInfo(sessionKeepAlive: SessionKeepAliveMode = SessionKeepAliveMode.ACTIVITY,
                                     authHeaders?: Partial<AuthHeaders> | (() => Promise<Partial<AuthHeaders>>)): ConnectionInfo {
    const connectionInfo = new ConnectionInfo()
    // @ts-ignore
    connectionInfo.host = inject('KINOTIC_HOST')
    // @ts-ignore
    connectionInfo.port = inject('KINOTIC_PORT')
    connectionInfo.maxConnectionAttempts = 3
    connectionInfo.sessionKeepAlive = sessionKeepAlive
    connectionInfo.webSocketFactory = authedWebSocketFactory(connectionInfo.host as string,
                                                             connectionInfo.port as number,
                                                             authHeaders)
    return connectionInfo
}
