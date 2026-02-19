import {v4 as uuidv4} from 'uuid'
import {afterAll, beforeAll, describe, expect, it} from 'vitest'
import {WebSocket} from 'ws'
import {ConnectedInfo, Continuum, Event, EventConstants, IEvent} from '../src'
import {createConnectionInfo, logFailure, validateConnectedInfo} from './TestHelper'

// This is required when running Continuum from node
Object.assign(global, { WebSocket})

describe('Continuum RPC Tests', () => {

    beforeAll(async () => {
        const connectionInfo = createConnectionInfo()
        let connectedInfo: ConnectedInfo = await logFailure(Continuum.connect(connectionInfo), 'Failed to connect to Continuum Gateway')
        validateConnectedInfo(connectedInfo)
    }, 1000 * 60 * 10) // 10 minutes

    afterAll(async () =>{
        await expect(Continuum.disconnect()).resolves.toBeUndefined()
    })

    it('should fail invalid service request', async () => {
        const toSend: IEvent = new Event('srv://org.mindignited.continuum.gatewayserver.clienttest.ITestService/testMethodWithString')
        toSend.setHeader(EventConstants.REPLY_TO_HEADER, '')
        toSend.setHeader(EventConstants.CONTENT_TYPE_HEADER, EventConstants.CONTENT_JSON)
        const correlationId = uuidv4()
        toSend.setHeader(EventConstants.CORRELATION_ID_HEADER, correlationId)
        toSend.setDataString('["Bob"]')

        let errorEncountered = new Promise<Error>((resolve, reject) => {
            Continuum.eventBus.fatalErrors.subscribe((error: Error) => {
                reject(error)
            })
        })

        Continuum.eventBus.send(toSend)

        await expect(errorEncountered).rejects.toThrowError('reply-to header invalid, scheme: null is not valid for service requests')

        expect(Continuum.eventBus.isConnectionActive()).toBeFalsy()

    })

})
