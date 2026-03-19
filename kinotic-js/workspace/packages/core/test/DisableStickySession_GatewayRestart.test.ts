import {afterAll, beforeAll, describe, expect, it} from 'vitest'
import {WebSocket} from 'ws'
import {ConnectedInfo, ConnectionInfo, KinoticSingleton} from '../src'
import {GenericContainer, PullPolicy, StartedTestContainer, Wait} from 'testcontainers'
import { logFailure, validateConnectedInfo } from './TestHelper'
import { TestService } from './ITestService'
import {KINOTIC_DOCKER_IMAGE} from './TestHelper.js'

// This is required when running Kinotic from node
Object.assign(global, { WebSocket})

describe('Disable Sticky Session Gateway Restart Reconnection Tests', () => {
    let container: StartedTestContainer
    let connectionInfo: ConnectionInfo = new ConnectionInfo()

    beforeAll(async () => {
        // Start the Kinotic Gateway container
        console.log('Starting Kinotic Gateway for sticky session gateway restart reconnection test')

        container = await new GenericContainer(KINOTIC_DOCKER_IMAGE)
            .withExposedPorts({container: 58503, host: 58599})
            .withEnvironment({SPRING_PROFILES_ACTIVE: "clienttest"})
            .withPullPolicy(PullPolicy.alwaysPull())
            .withWaitStrategy(Wait.forHttp('/', 58503))
            .withName('disable-sticky-session-reconnect-test')
            .start()

        // Create connection info with disableStickySession enabled
        connectionInfo.host = container.getHost()
        connectionInfo.port = 58599
        connectionInfo.maxConnectionAttempts = 0
        connectionInfo.disableStickySession = true
        connectionInfo.connectHeaders = async () => {return {login: 'kinotic', passcode: 'kinotic'}}

        console.log(`Kinotic Gateway running at ${connectionInfo.host}:${connectionInfo.port}`)
    }, 1000 * 60 * 10) // 10 minutes

    afterAll(async () => {
        // Clean up
        await container.stop({timeout: 60000, remove: true, removeVolumes: true})
    })

    it('should handle gateway restart with disableStickySession and reconnect', {"timeout": 1000 * 60 * 5}, async () => {

        // First connection and RPC call
        const continuum = new KinoticSingleton()
        let connectedInfo: ConnectedInfo = await logFailure(continuum.connect(connectionInfo),
                                                            'Failed to connect to Kinotic Gateway')
        validateConnectedInfo(connectedInfo)
        console.log(`Kinotic connected at ${connectionInfo.host}:${connectionInfo.port}`)

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
            .withPullPolicy(PullPolicy.alwaysPull())
            .withWaitStrategy(Wait.forHttp('/', 58503))
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
