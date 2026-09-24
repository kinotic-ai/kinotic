/**
 * What a workload listing may do to a workload beyond reading it and its logs, bound to a
 * caller allowed to: a platform operator stops and destroys any workload.
 */
export interface WorkloadOperations {
  stopWorkload(workloadId: string): Promise<void>
  destroyWorkload(workloadId: string): Promise<void>
}
