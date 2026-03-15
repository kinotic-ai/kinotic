import type {ILogManager} from '@/api/services/ILogManager'
import {LogManager} from '@/api/services/LogManager'
import type { IKinotic, KinoticPlugin } from '@kinotic-ai/core'
import { ApplicationService, type IApplicationService } from '@/api/services/IApplicationService'
import { ProjectService, type IProjectService } from '@/api/services/IProjectService'

export interface IOsApiExtension {
    applications: IApplicationService
    projects: IProjectService
    logManager: ILogManager
}

export const OsApiPlugin: KinoticPlugin<IOsApiExtension> = {
    install(kinotic: IKinotic): IOsApiExtension {
        return {
            applications: new ApplicationService(kinotic),
            projects: new ProjectService(kinotic),
            logManager: new LogManager(kinotic)
        }
    }
}
