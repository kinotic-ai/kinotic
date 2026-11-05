import type { IKinotic, KinoticPlugin } from '@kinotic-ai/core'
import { VmNodeServiceProxy, type IVmNodeService } from '@/api/services/workload/IVmNodeService'
import { WorkloadServiceProxy, type IWorkloadService } from '@/api/services/workload/IWorkloadService'
import { WorkloadOrchestrationService, type IWorkloadOrchestrationService } from '@/api/services/workload/IWorkloadOrchestrationService'
import { LogManager } from '@/api/services/LogManager'
import type { ILogManager } from '@/api/services/ILogManager'
import { KinoticClusterInfoService, type IKinoticClusterInfoService } from '@/api/services/IKinoticClusterInfoService'
import { ClusterVulnerabilityService, type IClusterVulnerabilityService } from '@/api/services/IClusterVulnerabilityService'
import { SystemOrganizationService, type ISystemOrganizationService } from '@/api/services/ISystemOrganizationService'
import { SystemMemberService, type ISystemMemberService } from '@/api/services/ISystemMemberService'
import { WatchEventService, type IWatchEventService } from '@/api/services/IWatchEventService'
import { LlmConfigurationService, type ILlmConfigurationService } from '@/api/services/ILlmConfigurationService'
import { SystemJobService, type ISystemJobService } from '@/api/services/ISystemJobService'

export interface ISystemApiExtension {
    vmNodes: IVmNodeService
    workloads: IWorkloadService
    workloadOrchestration: IWorkloadOrchestrationService
    logManager: ILogManager
    clusterInfo: IKinoticClusterInfoService
    clusterVulnerabilities: IClusterVulnerabilityService
    systemOrganizations: ISystemOrganizationService
    systemMembers: ISystemMemberService
    watchEvents: IWatchEventService
    llmConfigurations: ILlmConfigurationService
    systemJobs: ISystemJobService
}

export const SystemApiPlugin: KinoticPlugin<ISystemApiExtension> = {
    install(kinotic: IKinotic): ISystemApiExtension {
        return {
            vmNodes: new VmNodeServiceProxy(kinotic),
            workloads: new WorkloadServiceProxy(kinotic),
            workloadOrchestration: new WorkloadOrchestrationService(kinotic),
            logManager: new LogManager(kinotic),
            clusterInfo: new KinoticClusterInfoService(kinotic),
            clusterVulnerabilities: new ClusterVulnerabilityService(kinotic),
            systemOrganizations: new SystemOrganizationService(kinotic),
            systemMembers: new SystemMemberService(kinotic),
            watchEvents: new WatchEventService(kinotic),
            llmConfigurations: new LlmConfigurationService(kinotic),
            systemJobs: new SystemJobService(kinotic),
        }
    }
}
