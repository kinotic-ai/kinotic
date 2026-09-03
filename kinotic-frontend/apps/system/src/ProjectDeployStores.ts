import type { JobTaskNode } from '@kinotic-ai/frontend-common'

/** Mirrors DeployTarget on the server: what a deployment run's first task decided. */
export interface DeployTarget {
  nodeId: string
  hostDir: string
  runtimeWorkloadId: string | null
  syncWorkloadId: string
}

/**
 * The job scope names a project deployment run stores its outcomes under, mirroring
 * ProjectDeployStores on the server, and what they mean for the job page: a task node
 * carrying one of them is a task that ran a workload, which is how the page attaches that
 * workload's log to the task's row.
 */
export default class ProjectDeployStores {

  public static readonly DEPLOY_TARGET = 'deployTarget'
  public static readonly SYNC_WORKLOAD_ID = 'syncWorkloadId'

  /** Whether the task's row carries a workload log: the sync task's is the run's build log. */
  public static hasWorkloadLog(node: JobTaskNode): boolean {
    return node.storedName === ProjectDeployStores.SYNC_WORKLOAD_ID
  }

  /**
   * The workload whose log belongs to the task, or null while it is not yet known. The sync
   * task's workload is named by the resolved deploy target before the task runs, and by the
   * task itself once it completed.
   */
  public static workloadLogOf(node: JobTaskNode, root: JobTaskNode | null): string | null {
    let ret: string | null = null
    if (node.storedName === ProjectDeployStores.SYNC_WORKLOAD_ID) {
      if (typeof node.storedValue === 'string') {
        ret = node.storedValue
      } else {
        const target = root?.children
          .find(child => child.storedName === ProjectDeployStores.DEPLOY_TARGET)
          ?.storedValue as DeployTarget | undefined
        ret = target?.syncWorkloadId ?? null
      }
    }
    return ret
  }
}
