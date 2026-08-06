import type { ILogManager } from '@/api/services/ILogManager'
import { LogManager } from '@/api/services/LogManager'
import type { IKinotic, KinoticPlugin } from '@kinotic-ai/core'
import { ApplicationService, type IApplicationService } from '@/api/services/IApplicationService'
import { OrganizationService, type IOrganizationService } from '@/api/services/IOrganizationService'
import { ProjectService, type IProjectService } from '@/api/services/IProjectService'
import { EntityDefinitionService, type IEntityDefinitionService } from '@/api/services/IEntityDefinitionService'
import {type INamedQueriesDefinitionService, NamedQueriesDefinitionService} from '@/api/services/INamedQueriesDefinitionService'
import { MigrationService, type IMigrationService } from '@/api/services/IMigrationService'
import { DataInsightsService, type IDataInsightsService } from '@/api/services/IDataInsightsService'
import { VmNodeServiceProxy, type IVmNodeService } from '@/api/services/IVmNodeService'
import { WorkloadServiceProxy, type IWorkloadService } from '@/api/services/IWorkloadService'
import { MemberService, type IMemberService } from '@/api/services/IMemberService'
import { InviteEmailTemplateService, type IInviteEmailTemplateService } from '@/api/services/IInviteEmailTemplateService'
import { OAuthApprovalService, type IOAuthApprovalService } from '@/api/services/IOAuthApprovalService'
import { DelegateService, type IDelegateService } from '@/api/services/IDelegateService'
import { MachineService, type IMachineService } from '@/api/services/IMachineService'
import { GitHubAppInstallationService, type IGitHubAppInstallationService } from '@/api/services/IGitHubAppInstallationService'

export interface IOsApiExtension {
    applications: IApplicationService
    organizations: IOrganizationService
    projects: IProjectService
    logManager: ILogManager
    entityDefinitions: IEntityDefinitionService
    namedQueriesDefinitions: INamedQueriesDefinitionService
    migrations: IMigrationService
    dataInsights: IDataInsightsService
    vmNodes: IVmNodeService
    workloads: IWorkloadService
    members: IMemberService
    inviteEmailTemplates: IInviteEmailTemplateService
    oauthApproval: IOAuthApprovalService
    delegates: IDelegateService
    machines: IMachineService
    githubAppInstallations: IGitHubAppInstallationService
}

export const OsApiPlugin: KinoticPlugin<IOsApiExtension> = {
    install(kinotic: IKinotic): IOsApiExtension {
        return {
            applications: new ApplicationService(kinotic),
            organizations: new OrganizationService(kinotic),
            projects: new ProjectService(kinotic),
            logManager: new LogManager(kinotic),
            entityDefinitions: new EntityDefinitionService(kinotic),
            namedQueriesDefinitions: new NamedQueriesDefinitionService(kinotic),
            migrations: new MigrationService(kinotic),
            dataInsights: new DataInsightsService(kinotic),
            vmNodes: new VmNodeServiceProxy(kinotic),
            workloads: new WorkloadServiceProxy(kinotic),
            members: new MemberService(kinotic),
            inviteEmailTemplates: new InviteEmailTemplateService(kinotic),
            oauthApproval: new OAuthApprovalService(kinotic),
            delegates: new DelegateService(kinotic),
            machines: new MachineService(kinotic),
            githubAppInstallations: new GitHubAppInstallationService(kinotic),
        }
    }
}
