import type { IKinotic, KinoticPlugin } from '@kinotic-ai/core'
import { EntitiesService, type IEntitiesService } from '@/api/IEntitiesService'
import { AdminEntitiesService, type IAdminEntitiesService } from '@/api/IAdminEntitiesService'

export interface IPersistenceExtension {
    entities: IEntitiesService
    adminEntities: IAdminEntitiesService
}

export const PersistencePlugin: KinoticPlugin<IPersistenceExtension> = {
    install(kinotic: IKinotic): IPersistenceExtension {
        return {
            entities: new EntitiesService(kinotic),
            adminEntities: new AdminEntitiesService(kinotic),
        }
    }
}
