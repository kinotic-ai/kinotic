import {createGlobalSetup} from '../setup.js'

// The node-failure suite kills and restarts nodes of a two-node app server cluster, so it runs on its own stack.
// The system server is in it because an app node reports ready once the platform store runs a model, which the
// system server's reconciler writes; the management server is where the suite creates the probe's application
// and grants its runtime
const globalSetup = createGlobalSetup(['kinotic-server-management', 'kinotic-server-system', 'kinotic-server-app',
                                       'kinotic-server-app-2'])

export const setup = globalSetup.setup

export const teardown = globalSetup.teardown
