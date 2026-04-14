import {ConnectedInfo, ConnectHeaders, ConnectionInfo} from '../src'
import { expect, inject } from 'vitest'
import * as fs from 'fs'
import * as path from 'path'

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
    expect(connectedInfo.sessionId).toBeDefined()
    expect(connectedInfo.participant.id).toBeDefined()
    expect(connectedInfo.participant.roles).toBeDefined()
    expect(connectedInfo.participant.roles.length).toBe(1)
    if (roles) {
        expect(connectedInfo.participant.roles).toEqual(roles)
    } else {
        expect(connectedInfo.participant.roles[0]).toBe('ADMIN')
    }
}

export function createConnectionInfo(disableStickySession: boolean = false,
                                           connectHeaders?: ConnectHeaders | (() => Promise<ConnectHeaders>)): ConnectionInfo {
    const connectionInfo = new ConnectionInfo()
    // @ts-ignore
    connectionInfo.host = inject('KINOTIC_HOST')
    // @ts-ignore
    connectionInfo.port = inject('KINOTIC_PORT')
    connectionInfo.maxConnectionAttempts = 3
    connectionInfo.connectHeaders = connectHeaders || { login: 'kinotic@kinotic.local', passcode: 'kinotic' }
    connectionInfo.disableStickySession = disableStickySession
    return connectionInfo
}
