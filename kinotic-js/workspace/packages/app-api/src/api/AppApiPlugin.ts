import type { IKinotic, KinoticPlugin } from '@kinotic-ai/core'
import { TenantService, type ITenantService } from '@/api/services/ITenantService'
import { TenantMemberService, type ITenantMemberService } from '@/api/services/security/ITenantMemberService'

export interface IAppApiExtension {
    tenant: ITenantService
    tenantMembers: ITenantMemberService
}

export const AppApiPlugin: KinoticPlugin<IAppApiExtension> = {
    install(kinotic: IKinotic): IAppApiExtension {
        return {
            tenant: new TenantService(kinotic),
            tenantMembers: new TenantMemberService(kinotic),
        }
    }
}
