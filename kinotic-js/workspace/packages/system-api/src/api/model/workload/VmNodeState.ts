import type { VmNodeStatusType } from '@/api/model/workload/VmNodeStatusType'

/**
 * The reconciled state of a node: the shape both the intent and the observation take, so a node is
 * in its desired state exactly when the two are equal. Intent is that the node takes workloads;
 * observation is what the node reports it can do.
 */
export interface VmNodeState {
    /** Whether the node takes workloads. */
    phase: VmNodeStatusType
}
