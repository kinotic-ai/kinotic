// @ts-ignore for some reason intellij is complaining about this even though esModuleInterop is enabled
import path from 'node:path'
// @ts-ignore
import os from 'node:os'
import {StartedDockerComposeEnvironment, DockerComposeEnvironment, Wait} from 'testcontainers'
import {TestProject} from 'vitest/node.js'


let environment: StartedDockerComposeEnvironment

function isOSX_M1() {
    const arch = os.arch()
    const platform = os.platform()
    return platform === 'darwin' && arch === 'arm64'
}

// Run once before all tests
export async function setup(project: TestProject) {
    // @ts-ignore
    if(import.meta.env.VITE_USE_KINOTIC_DOCKER === 'true') {
        console.log('Starting Structures...')

        const resolvedPath = path.resolve('../../deployment/docker-compose/')
        const files = ['compose.kinotic-e2e-test.yml']
        if (isOSX_M1()) {
            files.push('compose.ek-m4.override.yml')
        }
        environment = await new DockerComposeEnvironment(resolvedPath, files)
            .withWaitStrategy('kinotic-elasticsearch', Wait.forHttp('/_cluster/health', 9200))
            .withWaitStrategy('kinotic-server', Wait.forHttp('/health', 9090))
            .withEnvironmentFile(path.resolve('../../', 'gradle.properties'))
            .up(['kinotic-elasticsearch', 'kinotic-server'])

        const container = environment.getContainer('kinotic-server')

        // @ts-ignore
        project.provide('KINOTIC_HOST', container.getHost())
        // @ts-ignore
        project.provide('KINOTIC_PORT', container.getMappedPort(58503))
        // @ts-ignore
        project.provide('KINOTIC_OPENAPI_PORT', container.getMappedPort(8080))

        console.log('Structures started.')
    }else{
        // @ts-ignore
        project.provide('KINOTIC_HOST', '127.0.0.1')
        // @ts-ignore
        project.provide('KINOTIC_PORT', 58503)
        // @ts-ignore
        project.provide('KINOTIC_OPENAPI_PORT', 8080)
        console.log('Skipping Structures setup because VITE_USE_KINOTIC_DOCKER is false')
    }
}

// Run once after all tests
export async function teardown() {
    // @ts-ignore
    if(import.meta.env.VITE_USE_KINOTIC_DOCKER === 'true') {
        console.log('Shutting down Structures...')
        await environment?.down()
        console.log('Structures shut down.')
    }else{
        console.log('Skipping Structures teardown because VITE_USE_KINOTIC_DOCKER is false')
    }
}
