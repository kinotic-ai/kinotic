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
            .withWaitStrategy(Wait.forHttp('/', 58503).forStatusCode(200))
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
    console.log('Shutting down Kinotic Gateway...')
    await container.stop()
    console.log('Kinotic Gateway shut down.')
}



