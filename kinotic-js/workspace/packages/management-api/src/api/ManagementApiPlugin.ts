import type { IKinotic, KinoticPlugin } from '@kinotic-ai/core'
import { ApplicationService, type IApplicationService } from '@/api/services/IApplicationService'
import { ProjectService, type IProjectService } from '@/api/services/IProjectService'
import { EntityDefinitionService, type IEntityDefinitionService } from '@/api/services/IEntityDefinitionService'
import {type INamedQueriesDefinitionService, NamedQueriesDefinitionService} from '@/api/services/INamedQueriesDefinitionService'
import { MigrationService, type IMigrationService } from '@/api/services/IMigrationService'
import { DataInsightsService, type IDataInsightsService } from '@/api/services/IDataInsightsService'
import { LogService, type ILogService } from '@/api/services/ILogService'
import { MemberService, type IMemberService } from '@/api/services/IMemberService'
import { InviteEmailTemplateService, type IInviteEmailTemplateService } from '@/api/services/IInviteEmailTemplateService'
import { OAuthApprovalService, type IOAuthApprovalService } from '@/api/services/IOAuthApprovalService'
import { DelegateService, type IDelegateService } from '@/api/services/IDelegateService'
import { ProfileService, type IProfileService } from '@/api/services/IProfileService'
import { MachineService, type IMachineService } from '@/api/services/IMachineService'
import { GitHubAppInstallationService, type IGitHubAppInstallationService } from '@/api/services/IGitHubAppInstallationService'
import { JobMonitoringService, type IJobMonitoringService } from '@/api/services/IJobMonitoringService'
import { KinoticClusterInfoService, type IKinoticClusterInfoService } from '@/api/services/IKinoticClusterInfoService'
import { SystemOrganizationService, type ISystemOrganizationService } from '@/api/services/ISystemOrganizationService'

export interface IManagementApiExtension {
    applications: IApplicationService
    clusterInfo: IKinoticClusterInfoService
    systemOrganizations: ISystemOrganizationService
    projects: IProjectService
    entityDefinitions: IEntityDefinitionService
    namedQueriesDefinitions: INamedQueriesDefinitionService
    migrations: IMigrationService
    dataInsights: IDataInsightsService
    jobMonitoring: IJobMonitoringService
    logs: ILogService
    members: IMemberService
    inviteEmailTemplates: IInviteEmailTemplateService
    oauthApproval: IOAuthApprovalService
    delegates: IDelegateService
    profile: IProfileService
    machines: IMachineService
    githubAppInstallations: IGitHubAppInstallationService
}

export const ManagementApiPlugin: KinoticPlugin<IManagementApiExtension> = {
    install(kinotic: IKinotic): IManagementApiExtension {
        return {
            applications: new ApplicationService(kinotic),
            clusterInfo: new KinoticClusterInfoService(kinotic),
            systemOrganizations: new SystemOrganizationService(kinotic),
            projects: new ProjectService(kinotic),
            entityDefinitions: new EntityDefinitionService(kinotic),
            namedQueriesDefinitions: new NamedQueriesDefinitionService(kinotic),
            migrations: new MigrationService(kinotic),
            dataInsights: new DataInsightsService(kinotic),
            jobMonitoring: new JobMonitoringService(kinotic),
            logs: new LogService(kinotic),
            members: new MemberService(kinotic),
            inviteEmailTemplates: new InviteEmailTemplateService(kinotic),
            oauthApproval: new OAuthApprovalService(kinotic),
            delegates: new DelegateService(kinotic),
            profile: new ProfileService(kinotic),
            machines: new MachineService(kinotic),
            githubAppInstallations: new GitHubAppInstallationService(kinotic),
        }
    }
}
