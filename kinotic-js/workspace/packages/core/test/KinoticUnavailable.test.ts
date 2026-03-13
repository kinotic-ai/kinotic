import {describe, expect, it} from 'vitest'
import {WebSocket} from 'ws'
import {ConnectedInfo, ConnectionInfo, Kinotic, KinoticSingleton} from '../src'
import { GenericContainer, PullPolicy, StartedTestContainer, Wait } from 'testcontainers'
import {TestService} from './ITestService.js'
import { logFailure, validateConnectedInfo } from './TestHelper'
import {KINOTIC_DOCKER_IMAGE} from './TestHelper.js'

// This is required when running Kinotic from node
Object.assign(global, { WebSocket})

// These tests live in their own fle because if working improperly, the can cause the test to hang
describe('Kinotic Unavailable Tests', () => {

    it('should fail fast on connection attempt', async () => {
        const host: string = 'notavailable'
        const port: number = 58503
        console.log(`Trying to Connecting to Unavailable Kinotic Gateway`)
        await expect(Kinotic.connect({
                                           host:host,
                                           port:port,
                                           maxConnectionAttempts: 3,
                                           connectHeaders:{login: 'kinotic', passcode: 'kinotic'}
                                       }))
            .rejects.toThrowError(
                expect.stringMatching(
                    /^Max number of reconnection attempts reached\. Last WS Error getaddrinfo (ENOTFOUND|EAI_AGAIN) notavailable$/
                )
            )

        await expect(Kinotic.disconnect()).resolves.toBeUndefined()
    }, 1000 * 60 * 10) // 10 minutes

    it('should connect to gateway and then fail after reconnection attempts after gateway is offline',
       {"timeout": 1000 * 60 * 3},
       async () => {
           let container: StartedTestContainer
           let connectionInfo: ConnectionInfo = new ConnectionInfo()

           // Start the Kinotic Gateway container
           console.log('Starting Kinotic Gateway for sticky session gateway restart reconnection test')

           container = await new GenericContainer(KINOTIC_DOCKER_IMAGE)
               .withExposedPorts({container: 58503, host: 58590})
               .withEnvironment({SPRING_PROFILES_ACTIVE: "clienttest"})
               .withPullPolicy(PullPolicy.alwaysPull())
               .withWaitStrategy(Wait.forHttp('/', 58503))
               .withName('maxretries-container')
               .start()

           // Create connection info with disableStickySession enabled
           connectionInfo.host = container.getHost()
           connectionInfo.port = 58590
           connectionInfo.maxConnectionAttempts = 3
           connectionInfo.disableStickySession = false
           connectionInfo.connectHeaders = async () => {return {login: 'kinotic', passcode: 'kinotic'}}
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
