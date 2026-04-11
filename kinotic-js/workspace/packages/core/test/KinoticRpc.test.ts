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

    // --- Participant Context Tests ---

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

    it('should have participant parameter match context participant', async () => {
        const result = await TEST_SERVICE.verifyParticipantParameterMatchesContext()
        expect(result).toBeDefined()
        expect(result.length).toBeGreaterThan(0)
    })

    it('should get full participant with all fields from context', async () => {
        const result = await TEST_SERVICE.getFullParticipantFromContext()
        expect(result).toBeDefined()
        expect(result.id).toBeDefined()
        expect(result.id.length).toBeGreaterThan(0)
        expect(result.roles).toBeDefined()
        expect(Array.isArray(result.roles)).toBe(true)
        expect(result.roles.length).toBeGreaterThan(0)
    })

    it('should handle participant-only parameter method', async () => {
        const result = await TEST_SERVICE.getParticipantOnlyParam()
        expect(result).toBeDefined()
        expect(result.id).toBeDefined()
        expect(result.id.length).toBeGreaterThan(0)
        expect(result.roles).toBeDefined()
        expect(Array.isArray(result.roles)).toBe(true)
    })

    it('should get participant id through Mono reactive chain', async () => {
        const result = await TEST_SERVICE.getParticipantIdFromMonoChain()
        expect(result).toBeDefined()
        expect(result).toMatch(/^mono:/)
    })

    it('should get participant id from nested executeBlocking', async () => {
        const result = await TEST_SERVICE.getParticipantIdFromNestedExecuteBlocking()
        expect(result).toBeDefined()
        expect(result.length).toBeGreaterThan(0)
    })

    it('should return consistent participant id across repeated reads', async () => {
        const results = await TEST_SERVICE.getParticipantIdRepeated(10)
        expect(results).toBeDefined()
        expect(results.length).toBe(10)
        const firstId = results[0]
        for (const id of results) {
            expect(id).toBe(firstId)
        }
    })

    it('should match participant param and context with first-arg participant', async () => {
        const result = await TEST_SERVICE.participantFirstArgWithContext(' rocks')
        expect(result).toBeDefined()
        expect(result).toMatch(/ rocks$/)
    })

    it('should match participant param and context with last-arg participant', async () => {
        const result = await TEST_SERVICE.participantLastArgWithContext('Hello ')
        expect(result).toBeDefined()
        expect(result).toMatch(/^Hello /)
    })

    it('should maintain participant context isolation across concurrent requests', async () => {
        const results = await Promise.all([
            TEST_SERVICE.getParticipantIdFromContext(),
            TEST_SERVICE.getParticipantIdFromContext(),
            TEST_SERVICE.getParticipantIdFromContext(),
            TEST_SERVICE.getParticipantIdFromContext(),
            TEST_SERVICE.getParticipantIdFromContext(),
        ])
        expect(results.length).toBe(5)
        const firstId = results[0]
        for (const id of results) {
            expect(id).toBe(firstId)
        }
    })

    it('should maintain participant across mixed concurrent requests', async () => {
        const [contextId, dispatchId, blockingId, monoId, fullParticipant] = await Promise.all([
            TEST_SERVICE.getParticipantIdFromContext(),
            TEST_SERVICE.getParticipantIdFromContextViaDispatch(),
            TEST_SERVICE.getParticipantIdFromContextInExecuteBlocking(),
            TEST_SERVICE.getParticipantIdFromMonoChain(),
            TEST_SERVICE.getFullParticipantFromContext(),
        ])
        expect(contextId).toBeDefined()
        expect(contextId).toBe(dispatchId)
        expect(contextId).toBe(blockingId)
        expect('mono:' + contextId).toBe(monoId)
        expect(fullParticipant.id).toBe(contextId)
    })

})
