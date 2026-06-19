import { ConnectionInfo, createAuthenticatedWebSocketFactory, KinoticSingleton } from '@kinotic-ai/core'
import { KinoticOsCredentialsAuthProvider, OsApiPlugin } from '@kinotic-ai/os-api'
import { PersistencePlugin } from '@kinotic-ai/persistence'

const ORGANIZATION_ID = 'kinotic-test'
const APP_USER_PASSWORD = 'kinotic'

/**
 * Returns the email of the APPLICATION-scoped IamUser the load generator writes data as for a
 * given application. These users are seeded by the V6__load_generator_fixtures migration —
 * client-side user creation was removed, so they are pre-created rather than provisioned here.
 */
function appUserEmail(applicationId: string): string {
    return `app-${applicationId}@kinotic.local`
}

/**
 * Creates a fresh {@link KinoticSingleton} connected as the seeded APPLICATION-scoped user for
 * the given application. Has OsApiPlugin and PersistencePlugin installed so it can back
 * EntityRepository instances used for entity-data CRUD. The caller is responsible for disconnecting.
 */
export async function initKinoticAppClient(baseConnectionInfo: ConnectionInfo,
                                           applicationId: string): Promise<KinoticSingleton> {
    const appKinotic = new KinoticSingleton()
    appKinotic.use(OsApiPlugin).use(PersistencePlugin)

    const connectionInfo = new ConnectionInfo()
    connectionInfo.host = baseConnectionInfo.host
    connectionInfo.port = baseConnectionInfo.port
    connectionInfo.useSSL = baseConnectionInfo.useSSL
    connectionInfo.webSocketFactory = createAuthenticatedWebSocketFactory(
        connectionInfo,
        new KinoticOsCredentialsAuthProvider(appUserEmail(applicationId), APP_USER_PASSWORD, ORGANIZATION_ID, applicationId)
    )

    await appKinotic.connect(connectionInfo)

    return appKinotic
}
