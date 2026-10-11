// The zone constants live in @kinotic-ai/management-api's PlatformZones; the application plane's zone is
// re-exported here so this package's consumers can name the zone its services occupy
export { APP_API_ZONE } from '@kinotic-ai/management-api'

// Models
export * from '@/api/model/Tenant'

// Services
export * from '@/api/services/ITenantService'
export * from '@/api/services/security/ITenantMemberService'

// Plugin
export * from '@/api/AppApiPlugin'

import type { IAppApiExtension } from '@/api/AppApiPlugin'

declare module '@kinotic-ai/core' {
    interface KinoticSingleton extends IAppApiExtension {}
}
