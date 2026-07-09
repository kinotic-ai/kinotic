import {v4 as uuidv4} from 'uuid'
import {afterAll, beforeAll, describe, expect, it} from 'vitest'
import {WebSocket} from 'ws'
import {ConnectedInfo, Kinotic, Event, EventConstants, type IEvent} from '../src'
import {createConnectionInfo, logFailure, validateConnectedInfo} from './TestHelper'

// This is required when running Kinotic from node
Object.assign(global, { WebSocket})

describe('Kinotic JS', () => {
  describe('packages/core', () => {
    describe('Kinotic RPC Tests', () => {

        beforeAll(async () => {
            const connectionInfo = createConnectionInfo()
            let connectedInfo: ConnectedInfo = await logFailure(Kinotic.connect(connectionInfo), 'Failed to connect to Kinotic Gateway')
            validateConnectedInfo(connectedInfo)
        }, 1000 * 60 * 10) // 10 minutes

        afterAll(async () =>{
            await expect(Kinotic.disconnect()).resolves.toBeUndefined()
        })

        it('should fail invalid service request', async () => {
            // Target a zone this organization participant may send to, so the request reaches
            // reply-to validation (the behavior under test) rather than being denied by zone rules
            const toSend: IEvent = new Event('srv://os-api.org.kinotic.server.clienttest.ITestService/testMethodWithString')
            toSend.setHeader(EventConstants.REPLY_TO_HEADER, '')
            toSend.setHeader(EventConstants.CONTENT_TYPE_HEADER, EventConstants.CONTENT_JSON)
            const correlationId = uuidv4()
            toSend.setHeader(EventConstants.CORRELATION_ID_HEADER, correlationId)
            toSend.setDataString('["Bob"]')

            let errorEncountered = new Promise<Error>((resolve) => {
                Kinotic.eventBus.fatalErrors.subscribe((error: Error) => {
                    resolve(error)
                })
            })

            Kinotic.eventBus.send(toSend)

            const error = await errorEncountered
            expect(error.message).toBe('STOMP connection error')
            expect((error.cause as Error).message).toBe('reply-to header invalid, scheme: null is not valid for service requests')

            expect(Kinotic.eventBus.isConnectionActive()).toBeFalsy()

        })

    })
  })
})
