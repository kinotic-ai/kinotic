import type { IKinotic, KinoticPlugin } from '@kinotic-ai/core'
import { ApplicationService, type IApplicationService } from '@/api/IApplicationService'
import { ProjectService, type IProjectService } from '@/api/IProjectService'

export interface IDomainExtension {
    applications: IApplicationService
    projects: IProjectService
}

export const DomainPlugin: KinoticPlugin<IDomainExtension> = {
    install(kinotic: IKinotic): IDomainExtension {
        return {
            applications: new ApplicationService(kinotic),
            projects: new ProjectService(kinotic),
        }
    }
}
