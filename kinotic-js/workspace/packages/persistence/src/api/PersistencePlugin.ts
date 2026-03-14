import type { IKinotic, KinoticPlugin } from '@kinotic-ai/core'
import { EntityDefinitionService, type IEntityDefinitionService } from '@/api/IEntityDefinitionService'
import { EntitiesService, type IEntitiesService } from '@/api/IEntitiesService'
import { AdminEntitiesService, type IAdminEntitiesService } from '@/api/IAdminEntitiesService'
import { NamedQueriesService, type INamedQueriesService } from '@/api/INamedQueriesService'
import { MigrationService, type IMigrationService } from '@/api/IMigrationService'
import { DataInsightsService, type IDataInsightsService } from '@/api/IDataInsightsService'

export interface IPersistenceExtension {
    entityDefinitions: IEntityDefinitionService
    entities: IEntitiesService
    adminEntities: IAdminEntitiesService
    namedQueries: INamedQueriesService
    migrations: IMigrationService
    dataInsights: IDataInsightsService
}

export const PersistencePlugin: KinoticPlugin<IPersistenceExtension> = {
    install(kinotic: IKinotic): IPersistenceExtension {
        return {
            entityDefinitions: new EntityDefinitionService(kinotic),
            entities: new EntitiesService(kinotic),
            adminEntities: new AdminEntitiesService(kinotic),
            namedQueries: new NamedQueriesService(kinotic),
            migrations: new MigrationService(kinotic),
            dataInsights: new DataInsightsService(kinotic),
        }
    }
}
