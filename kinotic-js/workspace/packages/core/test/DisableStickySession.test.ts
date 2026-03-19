import {afterAll, beforeAll, describe, expect, it} from 'vitest'
import {WebSocket} from 'ws'
import {ConnectedInfo, Kinotic, ConnectionInfo, KinoticSingleton} from '../src'
import {TEST_SERVICE} from './ITestService'
import { createConnectionInfo, logFailure, validateConnectedInfo } from './TestHelper'

// This is required when running Kinotic from node
Object.assign(global, { WebSocket})

describe('Disable Sticky Session Tests', () => {
    let connectionInfo: ConnectionInfo

    beforeAll(async () => {
        console.log('Starting Kinotic Gateway for sticky session test')

        connectionInfo = createConnectionInfo(true)
    }, 1000 * 60 * 10) // 10 minutes

    afterAll(async () => {

    })

    it('should connect with disableStickySession and hard disconnect and reconnect', {"timeout": 1000 * 60 * 2}, async () => {
        // Connect to the gateway with disableStickySession enabled
        const continuum = new KinoticSingleton()
        let connectedInfo: ConnectedInfo = await logFailure(continuum.connect(connectionInfo),
                                                            'Failed to connect to Kinotic Gateway')
        validateConnectedInfo(connectedInfo)

        // We use force here true. Otherwise, the server will clean up the session
        await expect(continuum.disconnect(true)).resolves.toBeUndefined()

        connectedInfo = await logFailure(continuum.connect(connectionInfo),
                                            'Failed to connect to Kinotic Gateway with disableStickySession enabled')

        validateConnectedInfo(connectedInfo)

        await expect(continuum.disconnect()).resolves.toBeUndefined()
    })

    it('send RPC call with disableStickySession', {"timeout": 1000 * 60 * 2}, async () => {
        // First connection and RPC call
        let connectedInfo: ConnectedInfo = await logFailure(Kinotic.connect(connectionInfo),
                                                            'Failed to connect to Kinotic Gateway')

        validateConnectedInfo(connectedInfo)

        const firstResult = await TEST_SERVICE.testMethodWithString("FirstCall")
        expect(firstResult).toBe("Hello FirstCall")

        await expect(Kinotic.disconnect()).resolves.toBeUndefined()
    })

})
