import type { IKinotic, KinoticPlugin } from '@kinotic-ai/core'
import { VmNodeServiceProxy, type IVmNodeService } from '@/api/services/IVmNodeService'
import { WorkloadServiceProxy, type IWorkloadService } from '@/api/services/IWorkloadService'
import { WorkloadOrchestrationService, type IWorkloadOrchestrationService } from '@/api/services/IWorkloadOrchestrationService'

export interface ISystemApiExtension {
    vmNodes: IVmNodeService
    workloads: IWorkloadService
    workloadOrchestration: IWorkloadOrchestrationService
}

export const SystemApiPlugin: KinoticPlugin<ISystemApiExtension> = {
    install(kinotic: IKinotic): ISystemApiExtension {
        return {
            vmNodes: new VmNodeServiceProxy(kinotic),
            workloads: new WorkloadServiceProxy(kinotic),
            workloadOrchestration: new WorkloadOrchestrationService(kinotic),
        }
    }
}
