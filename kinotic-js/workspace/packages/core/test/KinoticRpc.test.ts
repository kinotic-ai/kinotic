import {afterAll, beforeAll, describe, expect, it} from 'vitest'
import {WebSocket} from 'ws'
import {ConnectedInfo, Kinotic} from '../src'
import {NON_EXISTENT_SERVICE} from './INonExistentService'
import {TEST_SERVICE} from './ITestService'
import {createConnectionInfo, logFailure, validateConnectedInfo} from './TestHelper'

// This is required when running Kinotic from node
Object.assign(global, { WebSocket})

describe('Kinotic RPC Tests', () => {

    beforeAll(async () => {
        const connectionInfo =  createConnectionInfo()
        let connectedInfo: ConnectedInfo = await logFailure(Kinotic.connect(connectionInfo), 'Failed to connect to Kinotic Gateway')
        validateConnectedInfo(connectedInfo)
    }, 1000 * 60 * 10) // 10 minutes

    afterAll(async () =>{
        await expect(Kinotic.disconnect()).resolves.toBeUndefined()
    })


    it('should execute method with string parameter', async () =>{
        await expect(TEST_SERVICE.testMethodWithString("Bob")).resolves.toBe("Hello Bob")
    })

    it('should return missing method error', async () => {
        await expect(TEST_SERVICE.testMissingMethod()).rejects.toThrowError('No method could be resolved for methodId /testMissingMethod')
    })

    it('should return missing service error', async () => {
        await expect(NON_EXISTENT_SERVICE.probablyNotHome()).rejects.toThrowError('(NO_HANDLERS,-1) No handlers for address srv://com.namespace.NonExistentService')
    })

    it('should get participant id from vert.x context', async () => {
        const result = await TEST_SERVICE.getParticipantIdFromContext()
        expect(result).toBeDefined()
        expect(result.length).toBeGreaterThan(0)
    })

    it('should get participant id from vert.x context via dispatch', async () => {
        const result = await TEST_SERVICE.getParticipantIdFromContextViaDispatch()
        expect(result).toBeDefined()
        expect(result.length).toBeGreaterThan(0)
    })

    it('should get participant id from vert.x context in executeBlocking', async () => {
        const result = await TEST_SERVICE.getParticipantIdFromContextInExecuteBlocking()
        expect(result).toBeDefined()
        expect(result.length).toBeGreaterThan(0)
    })

})
