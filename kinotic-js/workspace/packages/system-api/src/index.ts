// The zone constants live in @kinotic-ai/management-api's PlatformZones; the system zone is
// re-exported here so this package's consumers can name the zone its services occupy
export { SYSTEM_API_ZONE } from '@kinotic-ai/management-api'

// Models
export * from '@/api/model/workload/VmNode'
export * from '@/api/model/workload/VmNodeState'
export * from '@/api/model/workload/VmNodeStatusType'
export * from '@/api/model/workload/VmProviderType'
export * from '@/api/model/workload/VmNodeRegistration'
export * from '@/api/model/workload/WorkloadStatusReport'

// Services
export * from '@/api/services/workload/IVmNodeService'
export * from '@/api/services/workload/IWorkloadService'
export * from '@/api/services/workload/IWorkloadOrchestrationService'
export * from '@/api/services/workload/VmNodeOrchestrationServiceProxy'

// Plugin
export * from '@/api/SystemApiPlugin'

import type { ISystemApiExtension } from '@/api/SystemApiPlugin'

declare module '@kinotic-ai/core' {
    interface KinoticSingleton extends ISystemApiExtension {}
}
export * from '@/api/services/ILogManager'
export * from '@/api/services/LogManager'
export * from '@/api/model/cluster/KinoticClusterInfo'
export * from '@/api/model/cluster/KinoticNodeInfo'
export * from '@/api/services/IKinoticClusterInfoService'
export * from '@/api/services/ISystemOrganizationService'
export * from '@/api/services/ISystemMemberService'
