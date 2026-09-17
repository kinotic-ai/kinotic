import {ManagementApiPlugin} from '@kinotic-ai/management-api'
import {Kinotic} from '@kinotic-ai/core'
import {ensureNodeWebSocket} from '@kinotic-ai/core/node'
import {PersistencePlugin} from '@kinotic-ai/persistence'
// @ts-ignore
import path from 'node:path'
import {StartedDockerComposeEnvironment, DockerComposeEnvironment, Wait} from 'testcontainers'
// @ts-ignore
import {TestProject} from 'vitest/node.js'

ensureNodeWebSocket()

Kinotic.use(ManagementApiPlugin)
       .use(PersistencePlugin)

/** The STOMP port every kinotic-server container listens on. */
const STOMP_PORT = 58503

/** The vitest globalSetup pair (setup and teardown) for a suite run against the e2e compose stack. */
export interface GlobalSetup {
    setup(project: TestProject): Promise<void>
    teardown(): Promise<void>
}

/**
 * Builds the globalSetup of a suite that runs against the compose stack in
 * deployment/docker-compose/compose.kinotic-e2e-test.yml. With VITE_USE_KINOTIC_DOCKER=true the
 * setup brings up Elasticsearch, the migration and the given kinotic-server services, and provides
 * KINOTIC_HOST plus the mapped STOMP port of each server: KINOTIC_PORT for the first and
 * KINOTIC_PORT_2 for the second. Otherwise it points the suite at 127.0.0.1 on the ports the compose
 * file publishes, for a stack started by hand.
 * @param serverNames the kinotic-server services to start, in the order their ports are provided
 */
export function createGlobalSetup(serverNames: string[]): GlobalSetup {
    let environment: StartedDockerComposeEnvironment

    return {
        async setup(project: TestProject): Promise<void> {
            // @ts-ignore
            if(import.meta.env.VITE_USE_KINOTIC_DOCKER === 'true') {
                console.log('Starting Kinotic...')

                const resolvedPath = path.resolve('../../deployment/docker-compose/')
                const files = ['compose.kinotic-e2e-test.yml']
                let compose = new DockerComposeEnvironment(resolvedPath, files)
                    .withWaitStrategy('kinotic-elasticsearch', Wait.forHttp('/_cluster/health', 9200))
                    .withEnvironmentFile(path.resolve('../../', 'gradle.properties'))
                for(const serverName of serverNames){
                    compose = compose.withWaitStrategy(serverName, Wait.forHttp('/health', 9090))
                }
                environment = await compose.up(['kinotic-elasticsearch', ...serverNames])

                const ports: number[] = []
                for(const serverName of serverNames){
                    const container = environment.getContainer(serverName)
                    ports.push(container.getMappedPort(STOMP_PORT))

                    // Surface server-side failures in the test output: without this, a broken server startup
                    // only ever shows up as opaque timeouts in the suites
                    try {
                        const logStream = await container.logs()
                        logStream.on('data', (chunk: Buffer | string) => {
                            const text = String(chunk)
                            if (text.includes('ERROR')) {
                                console.error(`[${serverName}]`, text.trimEnd())
                            }
                        })
                    } catch (e) {
                        console.error(`Could not attach to ${serverName} logs`, e)
                    }
                }

                provideServers(project, environment.getContainer(serverNames[0]).getHost(), ports)

                console.log('Kinotic started.')
            }else{
                // the host ports compose.kinotic-e2e-test.yml publishes, one per server in order
                provideServers(project, '127.0.0.1', serverNames.map((_, index) => STOMP_PORT + index))
                console.log('Skipping Kinotic setup because VITE_USE_KINOTIC_DOCKER is false')
            }
        },

        async teardown(): Promise<void> {
            // @ts-ignore
            if(import.meta.env.VITE_USE_KINOTIC_DOCKER === 'true') {
                console.log('Shutting down Kinotic...')
                await environment?.down()
                console.log('Kinotic shut down.')
            }else{
                console.log('Skipping Kinotic teardown because VITE_USE_KINOTIC_DOCKER is false')
            }
        }
    }
}

function provideServers(project: TestProject, host: string, ports: number[]): void {
    // @ts-ignore
    project.provide('KINOTIC_HOST', host)
    // @ts-ignore
    project.provide('KINOTIC_PORT', ports[0])
    if(ports.length > 1){
        // @ts-ignore
        project.provide('KINOTIC_PORT_2', ports[1])
    }
}

const globalSetup = createGlobalSetup(['kinotic-server'])

// Run once before all tests
export const setup = globalSetup.setup

// Run once after all tests
export const teardown = globalSetup.teardown
