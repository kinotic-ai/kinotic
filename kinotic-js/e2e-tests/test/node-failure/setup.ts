import {createGlobalSetup} from '../setup.js'

// The node-failure suite kills and restarts nodes of a two-node app server cluster, so it runs on its own stack.
// The system server is in it because an app node reports ready once the platform store runs a model, which the
// system server's reconciler writes
const globalSetup = createGlobalSetup(['kinotic-server-system', 'kinotic-server-app', 'kinotic-server-app-2'])

export const setup = globalSetup.setup

export const teardown = globalSetup.teardown
