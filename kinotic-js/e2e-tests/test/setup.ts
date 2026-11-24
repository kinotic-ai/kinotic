import {ManagementApiPlugin} from '@kinotic-ai/management-api'
import {Kinotic} from '@kinotic-ai/core'
import {ensureNodeWebSocket} from '@kinotic-ai/core/node'
import {PersistencePlugin} from '@kinotic-ai/persistence'
// @ts-ignore
import path from 'node:path'
import {StartedDockerComposeEnvironment, DockerComposeEnvironment, Wait, type WaitStrategy, getContainerRuntimeClient} from 'testcontainers'
// @ts-ignore
import {TestProject} from 'vitest/node.js'

ensureNodeWebSocket()

Kinotic.use(ManagementApiPlugin)
       .use(PersistencePlugin)

/** The REST and STOMP port each server container listens on, by its compose service name. */
const SERVER_PORTS = {
    'kinotic-server-management': 58503,
    'kinotic-server-system': 58504,
    'kinotic-server-app': 58505,
    'kinotic-server-app-2': 58505,
}

/** A server of the e2e compose stack, by its compose service name. */
export type KinoticServerName = keyof typeof SERVER_PORTS

/** The port OpenFGA serves its HTTP API on, where its health endpoint answers. */
const OPENFGA_PORT = 8080

/** How long a server may take to answer /health, matching the start_period of its compose healthcheck. */
const SERVER_STARTUP_TIMEOUT_MS = 120_000

/** How much of the end of a server's output is printed when the server does not start. */
const STARTUP_FAILURE_OUTPUT_CHARS = 16_000

/** The host port compose.kinotic-e2e-test.yml publishes each server on, for a stack started by hand. */
const PUBLISHED_PORTS: Record<KinoticServerName, number> = {
    'kinotic-server-management': 58503,
    'kinotic-server-system': 58504,
    'kinotic-server-app': 58505,
    'kinotic-server-app-2': 58506,
}

/** The vitest globalSetup pair (setup and teardown) for a suite run against the e2e compose stack. */
export interface GlobalSetup {
    setup(project: TestProject): Promise<void>
    teardown(): Promise<void>
}

const STACK_TRACE_LINE = /^(\s+at |\s+\.\.\. \d+ more|Caused by: |\s+Suppressed: |[\w.$]+(Exception|Error)\b)/

/**
 * Waits for a server to answer /health while reading its output, for as long as the server runs: a line
 * holding ERROR, with the stack trace that follows it, is printed as it arrives, and the end of the output is
 * printed when the server does not answer in time.
 */
class ServerStartupWaitStrategy implements WaitStrategy {

    private readonly health: WaitStrategy

    constructor(private readonly serverName: KinoticServerName) {
        this.health = Wait.forHttp('/health', SERVER_PORTS[serverName]).withStartupTimeout(SERVER_STARTUP_TIMEOUT_MS)
    }

    async waitUntilReady(...args: Parameters<WaitStrategy['waitUntilReady']>): Promise<void> {
        let recentOutput = ''
        const client = await getContainerRuntimeClient()
        const output = await client.container.logs(args[0])
        let inError = false
        output.on('data', (chunk: Buffer | string) => {
            const text = String(chunk)
            recentOutput = (recentOutput + text).slice(-STARTUP_FAILURE_OUTPUT_CHARS)
            for (const line of text.split('\n').filter(line => line.trim() !== '')) {
                // a logged exception's stack trace follows its ERROR line, often in later chunks
                inError = line.includes('ERROR') || (inError && STACK_TRACE_LINE.test(line))
                if (inError) {
                    console.error(`[${this.serverName}]`, line.trimEnd())
                }
            }
        })
        try {
            await this.health.waitUntilReady(...args)
        } catch (e) {
            // testcontainers removes the whole stack as soon as one wait fails, so the output is gone by the
            // time compose.up rejects
            console.error(`[${this.serverName}] did not answer /health. The end of its output:\n${recentOutput}`)
            throw e
        }
    }

    withStartupTimeout(startupTimeoutMs: number): WaitStrategy {
        this.health.withStartupTimeout(startupTimeoutMs)
        return this
    }

    isStartupTimeoutSet(): boolean {
        return this.health.isStartupTimeoutSet()
    }

    getStartupTimeout(): number {
        return this.health.getStartupTimeout()
    }
}

/**
 * Builds the globalSetup of a suite that runs against the compose stack in
 * deployment/docker-compose/compose.kinotic-e2e-test.yml. With VITE_USE_KINOTIC_DOCKER=true the
 * setup brings up Elasticsearch, OpenFGA, the migration and the given servers, and provides KINOTIC_HOST plus
 * KINOTIC_PORTS, the mapped REST and STOMP port of each server by name. Otherwise it points the suite at
 * 127.0.0.1 on the ports the compose file publishes, for a stack started by hand.
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
                    // the OpenFGA image has no shell, which the default port wait strategy needs
                    .withWaitStrategy('openfga', Wait.forHttp('/healthz', OPENFGA_PORT))
                    .withEnvironmentFile(path.resolve('../../', 'gradle.properties'))
                    // with the engine's caches on, a call answers from a cached check for up to ten seconds after
                    // a grant or revoke, which every test that changes access would wait out
                    .withEnvironment({
                        OPENFGA_CHECK_QUERY_CACHE_ENABLED: 'false',
                        OPENFGA_CHECK_ITERATOR_CACHE_ENABLED: 'false',
                        OPENFGA_SHARED_ITERATOR_ENABLED: 'false',
                        OPENFGA_CACHE_CONTROLLER_ENABLED: 'false'
                    })
                for(const serverName of serverNames){
                    compose = compose.withWaitStrategy(serverName, new ServerStartupWaitStrategy(serverName))
                }
                environment = await compose.up(['kinotic-elasticsearch', ...serverNames])

                const ports: Partial<Record<KinoticServerName, number>> = {}
                for(const serverName of serverNames){
                    ports[serverName] = environment.getContainer(serverName).getMappedPort(SERVER_PORTS[serverName])
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

const globalSetup = createGlobalSetup(['kinotic-server-management', 'kinotic-server-system', 'kinotic-server-app'])

// Run once before all tests
export const setup = globalSetup.setup

// Run once after all tests
export const teardown = globalSetup.teardown
