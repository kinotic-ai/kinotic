import { ConnectionInfo, Kinotic, KinoticSingleton } from '@kinotic-ai/core'
import { AuthType, IamUser, OsApiPlugin } from '@kinotic-ai/os-api'
import { PersistencePlugin } from '@kinotic-ai/persistence'

const APP_USER_TENANT_ID = 'default'
const APP_USER_PASSWORD = 'kinotic'

/**
 * Returns a deterministic email for the APPLICATION-scoped IamUser the load
 * generator uses to write data into a given application.
 */
function appUserEmail(applicationId: string): string {
    return `app-${applicationId}@kinotic.local`
}

/**
 * Provisions an APPLICATION-scoped IamUser for the given application if one
 * does not already exist. Must be called while the global Kinotic is
 * authenticated as an ORGANIZATION user (the management connection).
 */
export async function createAppUserIfNotExist(applicationId: string): Promise<string> {
    const email = appUserEmail(applicationId)
    const existing = await Kinotic.iamUsers.findByEmailAndScope(email, 'APPLICATION', applicationId)
    if (existing == null) {
        const user = new IamUser()
        user.email = email
        user.displayName = `Load Generator App User (${applicationId})`
        user.authType = AuthType.LOCAL
        user.authScopeType = 'APPLICATION'
        user.authScopeId = applicationId
        user.tenantId = APP_USER_TENANT_ID
        await Kinotic.iamUsers.createUser(user, APP_USER_PASSWORD)
    }
    return email
}

/**
 * Creates a fresh {@link KinoticSingleton} connected as the APPLICATION-scoped
 * user returned by {@link createAppUserIfNotExist}. Has OsApiPlugin and
 * PersistencePlugin installed so it can back EntityRepository instances used
 * for entity-data CRUD. The caller is responsible for disconnecting.
 */
export async function initKinoticAppClient(baseConnectionInfo: ConnectionInfo,
                                           applicationId: string): Promise<KinoticSingleton> {
    const email = await createAppUserIfNotExist(applicationId)

    const appKinotic = new KinoticSingleton()
    appKinotic.use(OsApiPlugin).use(PersistencePlugin)

    await appKinotic.connect({
        ...baseConnectionInfo,
        connectHeaders: {
            login: email,
            passcode: APP_USER_PASSWORD,
            authScopeType: 'APPLICATION',
            authScopeId: applicationId
        }
    })

    return appKinotic
}
