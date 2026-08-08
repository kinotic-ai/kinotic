import {afterAll, beforeAll, describe, expect, it} from 'vitest'
import {ConnectedInfo, type ConnectOptions, KinoticSingleton, SessionKeepAliveMode} from '../src'
import {ensureNodeWebSocket} from '../src/node'
import {GenericContainer, type StartedTestContainer, Wait} from 'testcontainers'
import { logFailure, testCredentials, validateConnectedInfo } from './TestHelper'
import { TestService } from './ITestService'
import {KINOTIC_DOCKER_IMAGE} from './TestHelper.js'

// credential headers ride the WebSocket upgrade, which needs the header-capable ws WebSocket
ensureNodeWebSocket()

describe('Kinotic JS', () => {
  describe('packages/core', () => {
    describe('Disable Sticky Session Gateway Restart Reconnection Tests', () => {
        let container: StartedTestContainer
        let connectOptions: ConnectOptions

        beforeAll(async () => {
            // Start the Kinotic Gateway container
            console.log('Starting Kinotic Gateway for sticky session gateway restart reconnection test')

            container = await new GenericContainer(KINOTIC_DOCKER_IMAGE)
                .withExposedPorts({container: 58503, host: 58599})
                .withEnvironment({SPRING_PROFILES_ACTIVE: "clienttest"})
                .withWaitStrategy(Wait.forHttp('/health', 58503).forStatusCodeMatching(c => c === 200 || c === 204))
                .withName('disable-sticky-session-reconnect-test')
                .start()

            // Create connect options without keeping the session alive after disconnect
            connectOptions = {
                server: {host: container.getHost(), port: 58599},
                maxConnectionAttempts: 0,
                sessionKeepAlive: SessionKeepAliveMode.NONE,
                credentials: testCredentials()
            }

            console.log(`Kinotic Gateway running at ${connectOptions.server!.host}:${connectOptions.server!.port}`)
        }, 1000 * 60 * 10) // 10 minutes

        afterAll(async () => {
            // Clean up
            await container.stop({timeout: 60000, remove: true, removeVolumes: true})
        })

        it('should handle gateway restart with sessionKeepAlive NONE and reconnect', {"timeout": 1000 * 60 * 5}, async () => {

            // First connection and RPC call
            const continuum = new KinoticSingleton()
            let connectedInfo: ConnectedInfo = await logFailure(continuum.connect(connectOptions),
                                                                'Failed to connect to Kinotic Gateway')
            validateConnectedInfo(connectedInfo)
            console.log(`Kinotic connected at ${connectOptions.server!.host}:${connectOptions.server!.port}`)

            const testService = new TestService(continuum)

            const firstResult = await testService.testMethodWithString("FirstCall")
            expect(firstResult).toBe("Hello FirstCall")

            // Stop the gateway
            console.log('Stopping Kinotic Gateway...')
            await container.stop({timeout: 60000, remove: true, removeVolumes: true})
            // Wait a moment for cleanup
            await new Promise(resolve => setTimeout(resolve, 10000))
            console.log('Starting Kinotic Gateway again...')
            container = await new GenericContainer(KINOTIC_DOCKER_IMAGE)
                .withExposedPorts({container: 58503, host: 58599})
                .withEnvironment({SPRING_PROFILES_ACTIVE: "clienttest"})
                .withWaitStrategy(Wait.forHttp('/health', 58503).forStatusCodeMatching(c => c === 200 || c === 204))
                .withName('disable-sticky-session-reconnect-test')
                .start()

            // Update connection info with new port mapping
            console.log(`Kinotic Gateway restarted`)

            // Connect again and make another RPC call
            while(!continuum.eventBus.isConnected()){
                await new Promise(resolve => setTimeout(resolve, 5000))
                console.log('Waiting for Kinotic Gateway to restart...')
            }

            console.log('Kinotic Gateway restarted')

            const secondResult = await testService.testMethodWithString("SecondCall")
            expect(secondResult).toBe("Hello SecondCall")

            await continuum.disconnect()
        })

    })
  })
})
