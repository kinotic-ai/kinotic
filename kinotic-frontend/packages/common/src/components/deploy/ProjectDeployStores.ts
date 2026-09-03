import type { JobTaskNode } from '../grind/JobTaskNode'

/** Mirrors DeployTarget on the server: what a deployment run's first task decided. */
export interface DeployTarget {
  nodeId: string
  hostDir: string
  syncWorkloadId: string
  uiPublishWorkloadId: string
}

/** Mirrors MicroserviceArtifact on the server: one microservice of the deployed commit. */
export interface MicroserviceArtifact {
  name: string
  dir: string
  entry: string
}

/** Mirrors UiArtifact on the server: one UI of the deployed commit. */
export interface UiArtifact {
  name: string
  dir: string
}

/** Mirrors ProjectArtifacts on the server: what the sync workload found in the deployed commit. */
export interface ProjectArtifacts {
  microservices: MicroserviceArtifact[]
  uis: UiArtifact[]
}

/**
 * The job scope names a project deployment run stores its outcomes under, mirroring
 * ProjectDeployStores on the server, and what they mean for the job page: a task node
 * carrying one of them is a task that ran a workload, which is how the page attaches that
 * workload's log to the task's row.
 */
export default class ProjectDeployStores {

  public static readonly ARTIFACTS = 'artifacts'
  public static readonly DEPLOY_TARGET = 'deployTarget'
  public static readonly MICROSERVICE_DEPLOYMENTS = 'microserviceDeployments'
  public static readonly SYNC_WORKLOAD_ID = 'syncWorkloadId'
  public static readonly UI_DEPLOYMENTS = 'uiDeployments'

  /** The artifacts the task bound into the run, or null while the task has not completed. */
  public static artifactsOf(node: JobTaskNode): ProjectArtifacts | null {
    let ret: ProjectArtifacts | null = null
    if (ProjectDeployStores.hasArtifacts(node) && node.storedValue !== null && node.storedValue !== undefined) {
      ret = node.storedValue as ProjectArtifacts
    }
    return ret
  }

  /** Whether the task's row lists the artifacts the deployed commit contains. */
  public static hasArtifacts(node: JobTaskNode): boolean {
    return node.storedName === ProjectDeployStores.ARTIFACTS
  }

  /** Whether the task's row has a detail pane: the artifacts it bound, or the workload log it ran. */
  public static hasDetail(node: JobTaskNode): boolean {
    return ProjectDeployStores.hasArtifacts(node) || ProjectDeployStores.hasWorkloadLog(node)
  }

  /**
   * Whether the task's row carries a workload log: the sync task's is the run's build log, the
   * publish task's is the upload log.
   */
  public static hasWorkloadLog(node: JobTaskNode): boolean {
    return node.storedName === ProjectDeployStores.SYNC_WORKLOAD_ID
      || node.storedName === ProjectDeployStores.UI_DEPLOYMENTS
  }

  /**
   * The workload whose log belongs to the task, or null while it is not yet known. Both
   * workloads are named by the resolved deploy target before their tasks run; the sync task
   * also names its own once it completed.
   */
  public static workloadLogOf(node: JobTaskNode, root: JobTaskNode | null): string | null {
    let ret: string | null = null
    const target = root?.children
      .find(child => child.storedName === ProjectDeployStores.DEPLOY_TARGET)
      ?.storedValue as DeployTarget | undefined
    if (node.storedName === ProjectDeployStores.SYNC_WORKLOAD_ID) {
      ret = typeof node.storedValue === 'string' ? node.storedValue : target?.syncWorkloadId ?? null
    } else if (node.storedName === ProjectDeployStores.UI_DEPLOYMENTS) {
      ret = target?.uiPublishWorkloadId ?? null
    }
    return ret
  }
}
