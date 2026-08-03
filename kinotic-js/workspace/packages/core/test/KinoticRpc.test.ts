import {afterAll, beforeAll, describe, expect, it} from 'vitest'
import {WebSocket} from 'ws'
import {firstValueFrom} from 'rxjs'
import {toArray} from 'rxjs/operators'
import {ConnectedInfo, Kinotic} from '../src'
import {NON_EXISTENT_SERVICE} from './INonExistentService'
import {TEST_SERVICE} from './ITestService'
import {createConnectionInfo, logFailure, validateConnectedInfo} from './TestHelper'

// This is required when running Kinotic from node
Object.assign(global, { WebSocket})

describe('Kinotic JS', () => {
  describe('packages/core', () => {
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
            await expect(NON_EXISTENT_SERVICE.probablyNotHome()).rejects.toThrowError('(NO_HANDLERS,-1) No handlers for address srv://os-api~com.namespace.NonExistentService')
        })

        // --- Binary Passthrough Tests ---

        it('should decode a byte[] return as a binary Uint8Array', async () => {
            const result = await TEST_SERVICE.getBinaryData()
            expect(result).toBeInstanceOf(Uint8Array)
            expect(Array.from(result)).toEqual([0, 1, 2, 3, 255, 254, 42, 255])
        })

        it('should decode a Flux<byte[]> stream as binary chunks', async () => {
            const chunks = await firstValueFrom(TEST_SERVICE.getBinaryDataStream().pipe(toArray()))
            expect(chunks.map(chunk => Array.from(chunk))).toEqual([
                [10, 20, 30],
                [40, 50],
                [60, 70, 80, 90],
            ])
        })

        // --- Participant Context Tests ---

        it('should get participant id from vert.x context', async () => {
            const result = await TEST_SERVICE.getParticipantIdFromContext()
            expect(result).toBeDefined()
            expect(result.length).toBeGreaterThan(0)
        })


        it('should get participant id from vert.x context in executeBlocking', async () => {
            const result = await TEST_SERVICE.getParticipantIdFromContextInExecuteBlocking()
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

        it('should verify participant param matches context inside Mono chain', async () => {
            const result = await TEST_SERVICE.verifyParticipantInMonoChain()
            expect(result).toBeDefined()
            expect(result.length).toBeGreaterThan(0)
        })

        it('should maintain participant across mixed concurrent requests', async () => {
            const [contextId, blockingId, monoId, fullParticipant] = await Promise.all([
                TEST_SERVICE.getParticipantIdFromContext(),
                TEST_SERVICE.getParticipantIdFromContextInExecuteBlocking(),
                TEST_SERVICE.getParticipantIdFromMonoChain(),
                TEST_SERVICE.getFullParticipantFromContext(),
            ])
            expect(contextId).toBeDefined()
            expect(contextId).toBe(blockingId)
            expect('mono:' + contextId).toBe(monoId)
            expect(fullParticipant.id).toBe(contextId)
        })

    })
  })
})
