import {GenericContainer, PullPolicy, Wait} from 'testcontainers'
import type {StartedTestContainer} from 'testcontainers'
import type {TestProject} from 'vitest/node'
import {KINOTIC_DOCKER_IMAGE} from './TestHelper.js'
import {config} from 'dotenv'

// Load .env files
config()

let container: StartedTestContainer

// Run once before all tests
export async function setup(project: TestProject) {
    if(process.env.USE_GATEWAY_DOCKER === 'true') {
        console.log('Starting Kinotic Gateway')

        container = await new GenericContainer(KINOTIC_DOCKER_IMAGE)
            .withExposedPorts(58503)
            .withEnvironment({SPRING_PROFILES_ACTIVE: "clienttest"})
            .withPullPolicy(PullPolicy.alwaysPull())
            // /health is the gateway readiness endpoint on the STOMP port; it returns 204 with no
            // health procedures registered (clienttest) and 200 once they are, so accept either.
            .withWaitStrategy(Wait.forHttp('/health', 58503).forStatusCodeMatching(c => c === 200 || c === 204))
            .start()

        // @ts-ignore
        project.provide('KINOTIC_HOST', container.getHost())
        // @ts-ignore
        project.provide('KINOTIC_PORT', container.getMappedPort(58503))

        console.log(`Kinotic Gateway started at ${container.getHost()}:${container.getMappedPort(58503)} `)
    }else{
        // @ts-ignore
        project.provide('KINOTIC_HOST', '127.0.0.1')
        // @ts-ignore
        project.provide('KINOTIC_PORT', 58503)
        console.log('Skipping Kinotic Gateway start because USE_GATEWAY_DOCKER is not set to true')
    }
}

// Run once after all tests
export async function teardown() {
    // container is only assigned when USE_GATEWAY_DOCKER started one; without the guard a
    // non-docker run fails in teardown and the suite exits 1 even when every test passed.
    if (!container) {
        return
    }
    console.log('Shutting down Kinotic Gateway...')
    await container.stop()
    console.log('Kinotic Gateway shut down.')
}



