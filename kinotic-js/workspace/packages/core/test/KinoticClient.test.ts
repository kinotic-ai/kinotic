import {describe, expect, it} from 'vitest'
import {WebSocket} from 'ws'
import {ConnectedInfo, KinoticSingleton, Event, EventConstants, ParticipantConstants} from '../src'
import {TestService} from './ITestService.js'
import {createConnectionInfo, logFailure, validateConnectedInfo} from './TestHelper'

// This is required when running Kinotic from node
Object.assign(global, { WebSocket})

describe('Kinotic Client Tests', () => {

    async function connectToKinotic(continuum: KinoticSingleton) {
        const connectionInfo = createConnectionInfo()
        const host = connectionInfo.host
        const port = connectionInfo.port as number
        return await logFailure(continuum.connect({
                                                      host: host,
                                                      port: port,
                                                      maxConnectionAttempts: 3,
                                                      connectHeaders: {
                                                          login: 'kinotic@kinotic.local',
                                                          passcode: 'kinotic'
                                                      }
                                                  }),
                                'Failed to connect to Kinotic Gateway')
    }

    it('should connect and disconnect', async () => {
        const continuum = new KinoticSingleton()
        const connectedInfo = await connectToKinotic(continuum)
        validateConnectedInfo(connectedInfo)

        await expect(continuum.disconnect()).resolves.toBeUndefined()
    })

    it('should connect and disconnect multiple times and still be able to call services', async () => {
        const continuum = new KinoticSingleton()
        const testService = new TestService(continuum);

        console.log(`Connecting to Kinotic Gateway running at the first time`)
        let connectedInfo = await connectToKinotic(continuum)
        validateConnectedInfo(connectedInfo)

        console.log(`Calling Service the first time`)
        console.log(await testService.testMethodWithString("Bob"))

        await expect(continuum.disconnect()).resolves.toBeUndefined()

        console.log(`Connecting to Kinotic Gateway running at the second time`)
        connectedInfo = await connectToKinotic(continuum)
        validateConnectedInfo(connectedInfo)

        console.log(`Calling Service the second time`)
        await expect(testService.testMethodWithString("Bob")).resolves.toBe("Hello Bob")

        await expect(continuum.disconnect()).resolves.toBeUndefined()

        console.log(`Connecting to Kinotic Gateway running at the third time`)
        connectedInfo = await connectToKinotic(continuum)
        validateConnectedInfo(connectedInfo)

        console.log(`Calling Service the third time`)
        await expect(testService.testMethodWithString("Bob")).resolves.toBe("Hello Bob")

        await expect(continuum.disconnect()).resolves.toBeUndefined()
    })

    it('should allow continuum CLI to connect but not send any data', async () => {
        const continuum = new KinoticSingleton()
        const testService = new TestService(continuum);
        console.log(`Connecting to Kinotic Gateway running at`)

        let connectedInfo: ConnectedInfo = await logFailure(continuum.connect(createConnectionInfo(false,
                                                                                                   {login: ParticipantConstants.CLI_PARTICIPANT_ID})),
                                                            'Failed to connect to Kinotic Gateway')

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
        const continuum = new KinoticSingleton()
        let connectedInfo: ConnectedInfo = await logFailure(continuum.connect(createConnectionInfo(false,
                                                                                                  {login: ParticipantConstants.CLI_PARTICIPANT_ID})),
                                                            'Failed to connect to Kinotic Gateway')
        validateConnectedInfo(connectedInfo, ['ANONYMOUS'])

        // We use force here true. Otherwise, the server will clean up the session
        await expect(continuum.disconnect(true)).resolves.toBeUndefined()

        connectedInfo = await logFailure(continuum.connect(createConnectionInfo(false,{session: connectedInfo.sessionId})),
            'Failed to connect to Kinotic Gateway with session id')

        validateConnectedInfo(connectedInfo, ['ANONYMOUS'])

        await expect(continuum.disconnect()).resolves.toBeUndefined()

    })

})
