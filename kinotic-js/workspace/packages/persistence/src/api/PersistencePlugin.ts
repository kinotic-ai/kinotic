import {AdminEntitiesRepository, type IAdminEntitiesRepository} from '@/api/IAdminEntitiesRepository'
import {EntitiesRepository, type IEntitiesRepository} from '@/api/IEntitiesRepository'
import type { IKinotic, KinoticPlugin } from '@kinotic-ai/core'

export interface IPersistenceExtension {
    entities: IEntitiesRepository
    adminEntities: IAdminEntitiesRepository
}

export const PersistencePlugin: KinoticPlugin<IPersistenceExtension> = {
    install(kinotic: IKinotic): IPersistenceExtension {
        return {
            entities: new EntitiesRepository(kinotic),
            adminEntities: new AdminEntitiesRepository(kinotic),
        }
    }
}
