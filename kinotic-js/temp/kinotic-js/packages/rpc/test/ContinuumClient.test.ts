import {afterAll, beforeAll, describe, expect, it} from 'vitest'
import {WebSocket} from 'ws'
import {ConnectedInfo, Continuum, ContinuumSingleton, Event, EventConstants, ParticipantConstants} from '../src'
import {TEST_SERVICE, TestService} from './ITestService.js'
import {createConnectionInfo, logFailure, validateConnectedInfo} from './TestHelper'

// This is required when running Continuum from node
Object.assign(global, { WebSocket})

describe('Continuum Client Tests', () => {

    async function connectToContinuum(continuum: ContinuumSingleton) {
        const connectionInfo = createConnectionInfo()
        const host = connectionInfo.host
        const port = connectionInfo.port as number
        return await logFailure(continuum.connect({
                                                      host: host,
                                                      port: port,
                                                      maxConnectionAttempts: 3,
                                                      connectHeaders: {
                                                          login: 'guest',
                                                          passcode: 'guest'
                                                      }
                                                  }),
                                'Failed to connect to Continuum Gateway')
    }

    it('should connect and disconnect', async () => {
        const continuum = new ContinuumSingleton()
        const connectedInfo = await connectToContinuum(continuum)
        validateConnectedInfo(connectedInfo)

        await expect(continuum.disconnect()).resolves.toBeUndefined()
    })

    it('should connect and disconnect multiple times and still be able to call services', async () => {
        const continuum = new ContinuumSingleton()
        const testService = new TestService(continuum);

        console.log(`Connecting to Continuum Gateway running at the first time`)
        let connectedInfo = await connectToContinuum(continuum)
        validateConnectedInfo(connectedInfo)

        console.log(`Calling Service the first time`)
        console.log(await testService.testMethodWithString("Bob"))

        await expect(continuum.disconnect()).resolves.toBeUndefined()

        console.log(`Connecting to Continuum Gateway running at the second time`)
        connectedInfo = await connectToContinuum(continuum)
        validateConnectedInfo(connectedInfo)

        console.log(`Calling Service the second time`)
        await expect(testService.testMethodWithString("Bob")).resolves.toBe("Hello Bob")

        await expect(continuum.disconnect()).resolves.toBeUndefined()

        console.log(`Connecting to Continuum Gateway running at the third time`)
        connectedInfo = await connectToContinuum(continuum)
        validateConnectedInfo(connectedInfo)

        console.log(`Calling Service the third time`)
        await expect(testService.testMethodWithString("Bob")).resolves.toBe("Hello Bob")

        await expect(continuum.disconnect()).resolves.toBeUndefined()
    })

    it('should allow continuum CLI to connect but not send any data', async () => {
        const continuum = new ContinuumSingleton()
        const testService = new TestService(continuum);
        console.log(`Connecting to Continuum Gateway running at`)

        let connectedInfo: ConnectedInfo = await logFailure(continuum.connect(createConnectionInfo(false,
                                                                                                   {login: ParticipantConstants.CLI_PARTICIPANT_ID})),
                                                            'Failed to connect to Continuum Gateway')

        validateConnectedInfo(connectedInfo, ['ANONYMOUS'])

        const promise = new Promise((resolve, reject) => {
            continuum.eventBus.fatalErrors.subscribe((error: Error) => {
                resolve(error)
            })
        })

        console.log('Sending invalid event from continuum client')
        continuum.eventBus.send(new Event(EventConstants.SERVICE_DESTINATION_PREFIX+ 'blah'))

        const error = await logFailure(promise, 'Failed to receive error from fatalErrors observable')

        expect(error).toBeDefined()

        // make sure client was automatically disconnected
        expect(continuum.eventBus.isConnectionActive(),
            'Client to be disconnected').toBe(false)

        await expect(continuum.disconnect()).resolves.toBeUndefined()

    })

    it('should allow connection with session id', async () => {
        const continuum = new ContinuumSingleton()
        let connectedInfo: ConnectedInfo = await logFailure(continuum.connect(createConnectionInfo(false,
                                                                                                  {login: ParticipantConstants.CLI_PARTICIPANT_ID})),
                                                            'Failed to connect to Continuum Gateway')
        validateConnectedInfo(connectedInfo, ['ANONYMOUS'])

        // We use force here true. Otherwise, the server will clean up the session
        await expect(continuum.disconnect(true)).resolves.toBeUndefined()

        connectedInfo = await logFailure(continuum.connect(createConnectionInfo(false,{session: connectedInfo.sessionId})),
            'Failed to connect to Continuum Gateway with session id')

        validateConnectedInfo(connectedInfo, ['ANONYMOUS'])

        await expect(continuum.disconnect()).resolves.toBeUndefined()

    })

})
