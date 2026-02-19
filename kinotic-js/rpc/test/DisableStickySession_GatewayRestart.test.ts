import {afterAll, beforeAll, describe, expect, it} from 'vitest'
import {WebSocket} from 'ws'
import {ConnectedInfo, ConnectionInfo, ContinuumSingleton} from '../src'
import {GenericContainer, PullPolicy, StartedTestContainer, Wait} from 'testcontainers'
import { logFailure, validateConnectedInfo } from './TestHelper'
import { TestService } from './ITestService'

// This is required when running Continuum from node
Object.assign(global, { WebSocket})

describe('Disable Sticky Session Gateway Restart Reconnection Tests', () => {
    let container: StartedTestContainer
    let connectionInfo: ConnectionInfo = new ConnectionInfo()

    beforeAll(async () => {
        // Start the Continuum Gateway container
        console.log('Starting Continuum Gateway for sticky session gateway restart reconnection test')

        container = await new GenericContainer('mindignited/continuum-gateway-server:latest')
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
        connectionInfo.connectHeaders = async () => {return {login: 'guest', passcode: 'guest'}}

        console.log(`Continuum Gateway running at ${connectionInfo.host}:${connectionInfo.port}`)
    }, 1000 * 60 * 10) // 10 minutes

    afterAll(async () => {
        // Clean up
        await container.stop({timeout: 60000, remove: true, removeVolumes: true})
    })

    it('should handle gateway restart with disableStickySession and reconnect', {"timeout": 1000 * 60 * 5}, async () => {

        // First connection and RPC call
        const continuum = new ContinuumSingleton()
        let connectedInfo: ConnectedInfo = await logFailure(continuum.connect(connectionInfo),
                                                            'Failed to connect to Continuum Gateway')
        validateConnectedInfo(connectedInfo)
        console.log(`Continuum connected at ${connectionInfo.host}:${connectionInfo.port}`)

        const testService = new TestService(continuum)

        const firstResult = await testService.testMethodWithString("FirstCall")
        expect(firstResult).toBe("Hello FirstCall")

        // Stop the gateway
        console.log('Stopping Continuum Gateway...')
        await container.stop({timeout: 60000, remove: true, removeVolumes: true})
        // Wait a moment for cleanup
        await new Promise(resolve => setTimeout(resolve, 10000))
        console.log('Starting Continuum Gateway again...')
        container = await new GenericContainer('mindignited/continuum-gateway-server:latest')
            .withExposedPorts({container: 58503, host: 58599})
            .withEnvironment({SPRING_PROFILES_ACTIVE: "clienttest"})
            .withPullPolicy(PullPolicy.alwaysPull())
            .withWaitStrategy(Wait.forHttp('/', 58503))
            .withName('disable-sticky-session-reconnect-test')
            .start()

        // Update connection info with new port mapping
        console.log(`Continuum Gateway restarted`)

        // Connect again and make another RPC call
        while(!continuum.eventBus.isConnected()){
            await new Promise(resolve => setTimeout(resolve, 5000))
            console.log('Waiting for Continuum Gateway to restart...')
        }

        console.log('Continuum Gateway restarted')

        const secondResult = await testService.testMethodWithString("SecondCall")
        expect(secondResult).toBe("Hello SecondCall")

        await continuum.disconnect()
    })

})
