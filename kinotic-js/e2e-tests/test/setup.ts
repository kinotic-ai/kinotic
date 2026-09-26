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

/** The REST and STOMP port each server container listens on, by its compose service name. */
const SERVER_PORTS = {
    'kinotic-org-server': 58503,
    'kinotic-system-server': 58504,
    'kinotic-app-server': 58505,
    'kinotic-app-server-2': 58505,
}

/** A server of the e2e compose stack, by its compose service name. */
export type KinoticServerName = keyof typeof SERVER_PORTS

/** The host port compose.kinotic-e2e-test.yml publishes each server on, for a stack started by hand. */
const PUBLISHED_PORTS: Record<KinoticServerName, number> = {
    'kinotic-org-server': 58503,
    'kinotic-system-server': 58504,
    'kinotic-app-server': 58505,
    'kinotic-app-server-2': 58506,
}

/** The vitest globalSetup pair (setup and teardown) for a suite run against the e2e compose stack. */
export interface GlobalSetup {
    setup(project: TestProject): Promise<void>
    teardown(): Promise<void>
}

/**
 * Builds the globalSetup of a suite that runs against the compose stack in
 * deployment/docker-compose/compose.kinotic-e2e-test.yml. With VITE_USE_KINOTIC_DOCKER=true the
 * setup brings up Elasticsearch, the migration and the given servers, and provides KINOTIC_HOST plus
 * KINOTIC_PORTS, the mapped REST and STOMP port of each server by name. Otherwise it points the suite
 * at 127.0.0.1 on the ports the compose file publishes, for a stack started by hand.
 * @param serverNames the servers to start
 */
export function createGlobalSetup(serverNames: KinoticServerName[]): GlobalSetup {
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
                    compose = compose.withWaitStrategy(serverName, Wait.forHttp('/health', SERVER_PORTS[serverName]))
                }
                environment = await compose.up(['kinotic-elasticsearch', ...serverNames])

                const ports: Partial<Record<KinoticServerName, number>> = {}
                for(const serverName of serverNames){
                    const container = environment.getContainer(serverName)
                    ports[serverName] = container.getMappedPort(SERVER_PORTS[serverName])

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
                const ports: Partial<Record<KinoticServerName, number>> = {}
                for(const serverName of serverNames){
                    ports[serverName] = PUBLISHED_PORTS[serverName]
                }
                provideServers(project, '127.0.0.1', ports)
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

function provideServers(project: TestProject, host: string, ports: Partial<Record<KinoticServerName, number>>): void {
    // @ts-ignore
    project.provide('KINOTIC_HOST', host)
    // @ts-ignore
    project.provide('KINOTIC_PORTS', ports)
}

const globalSetup = createGlobalSetup(['kinotic-org-server', 'kinotic-system-server', 'kinotic-app-server'])

// Run once before all tests
export const setup = globalSetup.setup

// Run once after all tests
export const teardown = globalSetup.teardown
