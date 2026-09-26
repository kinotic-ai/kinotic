import {createGlobalSetup} from '../setup.js'

// The node-failure suite kills and restarts nodes of a two-node app server cluster, so it runs on its own stack
const globalSetup = createGlobalSetup(['kinotic-app-server', 'kinotic-app-server-2'])

export const setup = globalSetup.setup

export const teardown = globalSetup.teardown
