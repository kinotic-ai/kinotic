import type { ILogManager } from '@/api/services/ILogManager'
import { LogManager } from '@/api/services/LogManager'
import type { IKinotic, KinoticPlugin } from '@kinotic-ai/core'
import { ApplicationService, type IApplicationService } from '@/api/services/IApplicationService'
import { ProjectService, type IProjectService } from '@/api/services/IProjectService'
import { EntityDefinitionService, type IEntityDefinitionService } from '@/api/services/IEntityDefinitionService'
import {type INamedQueriesDefinitionService, NamedQueriesDefinitionService} from '@/api/services/INamedQueriesDefinitionService'
import { MigrationService, type IMigrationService } from '@/api/services/IMigrationService'
import { DataInsightsService, type IDataInsightsService } from '@/api/services/IDataInsightsService'

export interface IOsApiExtension {
    applications: IApplicationService
    projects: IProjectService
    logManager: ILogManager
    entityDefinitions: IEntityDefinitionService
    namedQueriesDefinitions: INamedQueriesDefinitionService
    migrations: IMigrationService
    dataInsights: IDataInsightsService
}

export const OsApiPlugin: KinoticPlugin<IOsApiExtension> = {
    install(kinotic: IKinotic): IOsApiExtension {
        return {
            applications: new ApplicationService(kinotic),
            projects: new ProjectService(kinotic),
            logManager: new LogManager(kinotic),
            entityDefinitions: new EntityDefinitionService(kinotic),
            namedQueriesDefinitions: new NamedQueriesDefinitionService(kinotic),
            migrations: new MigrationService(kinotic),
            dataInsights: new DataInsightsService(kinotic),
        }
    }
}
