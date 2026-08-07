import {describe, expect, it} from 'vitest'
import {WebSocket} from 'ws'
import {ConnectedInfo, type ConnectOptions, Kinotic, KinoticSingleton, SessionKeepAliveMode} from '../src'
import { GenericContainer, type StartedTestContainer, Wait } from 'testcontainers'
import {TestService} from './ITestService.js'
import { authedWebSocketFactory, logFailure, validateConnectedInfo } from './TestHelper'
import {KINOTIC_DOCKER_IMAGE} from './TestHelper.js'

// This is required when running Kinotic from node
Object.assign(global, { WebSocket})

// These tests live in their own fle because if working improperly, the can cause the test to hang
describe('Kinotic JS', () => {
  describe('packages/core', () => {
    describe('Kinotic Unavailable Tests', () => {

        it('should fail fast on connection attempt', async () => {
            const host: string = 'notavailable'
            const port: number = 58503
            console.log(`Trying to Connecting to Unavailable Kinotic Gateway`)
            const ci: ConnectOptions = {host: ""}
            ci.host = host
            ci.port = port
            ci.maxConnectionAttempts = 3
            ci.webSocketFactory = authedWebSocketFactory(host, port)

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
               let connectionInfo: ConnectOptions = {host: ""}

               // Start the Kinotic Gateway container
               console.log('Starting Kinotic Gateway for sticky session gateway restart reconnection test')

               container = await new GenericContainer(KINOTIC_DOCKER_IMAGE)
                   .withExposedPorts({container: 58503, host: 58590})
                   .withEnvironment({SPRING_PROFILES_ACTIVE: "clienttest"})
                   .withWaitStrategy(Wait.forHttp('/health', 58503).forStatusCodeMatching(c => c === 200 || c === 204))
                   .withName('maxretries-container')
                   .start()

               // Create connection info with default activity-based session keep alive
               connectionInfo.host = container.getHost()
               connectionInfo.port = 58590
               connectionInfo.maxConnectionAttempts = 3
               connectionInfo.sessionKeepAlive = SessionKeepAliveMode.ACTIVITY
               connectionInfo.webSocketFactory = authedWebSocketFactory(connectionInfo.host, connectionInfo.port)
               console.log(`Kinotic Gateway running at ${connectionInfo.host}:${connectionInfo.port}`)

               const continuum = new KinoticSingleton()
               let connectedInfo: ConnectedInfo = await logFailure(continuum.connect(connectionInfo),
                                                                   'Failed to connect to Kinotic Gateway')
               validateConnectedInfo(connectedInfo)
               console.log(`Kinotic Gateway started at ${connectionInfo.host}:${connectionInfo.port}`)

               const testService = new TestService(continuum)

               // stop the gateway
               await container.stop()

               await expect(testService.testMethodWithString("Bob")).rejects.toThrowError(new Error('Connection disconnected'))

               await continuum.disconnect()

           })

    })
  })
})
