import {describe, expect, it} from 'vitest'
import {ConnectedInfo, type ConnectOptions, Kinotic, KinoticSingleton, SessionKeepAliveMode} from '../src'
import {ensureNodeWebSocket} from '../src/node'
import { GenericContainer, type StartedTestContainer, Wait } from 'testcontainers'
import {TestService} from './ITestService.js'
import { logFailure, testCredentials, validateConnectedInfo } from './TestHelper'
import {KINOTIC_DOCKER_IMAGE} from './TestHelper.js'

// credential headers ride the WebSocket upgrade, which needs the header-capable ws WebSocket
ensureNodeWebSocket()

// These tests live in their own fle because if working improperly, the can cause the test to hang
describe('Kinotic JS', () => {
  describe('packages/core', () => {
    describe('Kinotic Unavailable Tests', () => {

        it('should fail fast on connection attempt', async () => {
            console.log(`Trying to Connecting to Unavailable Kinotic Gateway`)
            const ci: ConnectOptions = {
                server: {host: 'notavailable', port: 58503},
                maxConnectionAttempts: 3,
                credentials: testCredentials()
            }

            // The reconnect-exhausted failure carries the underlying WS/DNS error as the Error cause
            // rather than concatenating its (environment-specific) text into the message.
            // connect() rejects with the fatal error's message string (StompConnectionManager
            // rejects with err.message); the underlying WS/DNS error is environment-specific, so
            // assert only the stable reconnect-exhausted message.
            const error: unknown = await Kinotic.connect(ci).then(() => null, (e) => e)
            expect(error).toBe('Max number of reconnection attempts reached')

            await expect(Kinotic.disconnect()).resolves.toBeUndefined()
        }, 1000 * 60 * 10) // 10 minutes

        it('should connect to gateway and then fail after reconnection attempts after gateway is offline',
           {"timeout": 1000 * 60 * 3},
           async () => {
               let container: StartedTestContainer

               // Start the Kinotic Gateway container
               console.log('Starting Kinotic Gateway for sticky session gateway restart reconnection test')

               container = await new GenericContainer(KINOTIC_DOCKER_IMAGE)
                   .withExposedPorts({container: 58503, host: 58590})
                   .withEnvironment({SPRING_PROFILES_ACTIVE: "clienttest"})
                   .withWaitStrategy(Wait.forHttp('/health', 58503).forStatusCodeMatching(c => c === 200 || c === 204))
                   .withName('maxretries-container')
                   .start()

               // Create connect options with default activity-based session keep alive
               const connectOptions: ConnectOptions = {
                   server: {host: container.getHost(), port: 58590},
                   maxConnectionAttempts: 3,
                   sessionKeepAlive: SessionKeepAliveMode.ACTIVITY,
                   credentials: testCredentials()
               }
               console.log(`Kinotic Gateway running at ${connectOptions.server!.host}:${connectOptions.server!.port}`)

               const continuum = new KinoticSingleton()
               let connectedInfo: ConnectedInfo = await logFailure(continuum.connect(connectOptions),
                                                                   'Failed to connect to Kinotic Gateway')
               validateConnectedInfo(connectedInfo)
               console.log(`Kinotic Gateway started at ${connectOptions.server!.host}:${connectOptions.server!.port}`)

               const testService = new TestService(continuum)

               // stop the gateway
               await container.stop()
               while (continuum.eventBus.isConnected()) {
                   await new Promise(resolve => setTimeout(resolve, 100))
               }

               // A request needs a live connection: it is stamped with the reply destination of the
               // connection it goes out on, so it fails here instead of waiting out the reconnects
               await expect(testService.testMethodWithString("Bob"))
                   .rejects.toThrowError(new Error('The event bus is not connected to the server'))

               await continuum.disconnect()

           })

    })
  })
})
