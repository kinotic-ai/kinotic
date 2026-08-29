// The zone constants live in @kinotic-ai/management-api's PlatformZones; the system zone is
// re-exported here so this package's consumers can name the zone its services occupy
export { SYSTEM_API_ZONE } from '@kinotic-ai/management-api'

// Models
export * from '@/api/model/workload/LogPolicy'
export * from '@/api/model/workload/NetworkMode'
export * from '@/api/model/workload/NetworkPolicy'
export * from '@/api/model/workload/PortMapping'
export * from '@/api/model/workload/PortProtocol'
export * from '@/api/model/workload/VmNode'
export * from '@/api/model/workload/VmNodeStatus'
export * from '@/api/model/workload/VmNodeStatusType'
export * from '@/api/model/workload/VmProviderType'
export * from '@/api/model/workload/VolumeMount'
export * from '@/api/model/workload/Workload'
export * from '@/api/model/workload/WorkloadStatus'
export * from '@/api/model/VmNodeRegistration'
export * from '@/api/model/WorkloadStatusReport'

// Services
export * from '@/api/services/IVmNodeService'
export * from '@/api/services/IWorkloadService'
export * from '@/api/services/IWorkloadOrchestrationService'
export * from '@/api/services/VmNodeOrchestrationServiceProxy'

// Plugin
export * from '@/api/SystemApiPlugin'

import type { ISystemApiExtension } from '@/api/SystemApiPlugin'

declare module '@kinotic-ai/core' {
    interface KinoticSingleton extends ISystemApiExtension {}
}
export * from '@/api/services/ILogManager'
export * from '@/api/services/LogManager'
export * from '@/api/model/KinoticClusterInfo'
export * from '@/api/model/KinoticNodeInfo'
export * from '@/api/services/IKinoticClusterInfoService'
export * from '@/api/services/ISystemOrganizationService'
