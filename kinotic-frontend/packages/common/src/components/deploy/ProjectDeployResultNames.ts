import { markRaw, type Component } from 'vue'
import { Crosshair, FolderGit2, Globe, ListTree, Package, Server } from '@lucide/vue'
import type { ProjectArtifacts } from '@kinotic-ai/management-api'
import type { JobTaskFailure } from '../grind/JobTaskFailure'
import type { JobTaskNode } from '../grind/JobTaskNode'

/** Mirrors DeployTarget on the server: what a deployment run's first task decided. */
export interface DeployTarget {
  nodeId: string
  hostDir: string
  syncWorkloadId: string
  uiPublishWorkloadId: string
  sbomWorkloadId: string
}

/**
 * The names a project deployment run's tasks store their results under, mirroring
 * ProjectDeployResultNames on the server, and what those results mean for the job page: each
 * step's icon, how its failure reads, which task's row lists the deployed commit's artifacts, and
 * which rows attach the log of the workload their task ran.
 */
export default class ProjectDeployResultNames {

  public static readonly ARTIFACTS = 'artifacts'
  public static readonly DEPLOY_TARGET = 'deployTarget'
  public static readonly MICROSERVICE_DEPLOYMENTS = 'microserviceDeployments'
  public static readonly SYNC_WORKLOAD_ID = 'syncWorkloadId'
  public static readonly UI_DEPLOYMENTS = 'uiDeployments'
  public static readonly SBOM = 'sbom'

  private static readonly ICONS: Record<string, Component> = {
    [ProjectDeployResultNames.DEPLOY_TARGET]: markRaw(Crosshair),
    [ProjectDeployResultNames.SYNC_WORKLOAD_ID]: markRaw(FolderGit2),
    [ProjectDeployResultNames.ARTIFACTS]: markRaw(Package),
    [ProjectDeployResultNames.MICROSERVICE_DEPLOYMENTS]: markRaw(Server),
    [ProjectDeployResultNames.UI_DEPLOYMENTS]: markRaw(Globe),
    [ProjectDeployResultNames.SBOM]: markRaw(ListTree)
  }

  /** How to start another deployment run, for a run that failed. */
  public static readonly RETRY_HINT = 'To try again, push a commit to the project\'s default branch; each push starts a new deployment run.'

  // Steps whose failure is the platform's to fix, whatever the project contains
  private static readonly PLATFORM_FAILURES: Record<string, string> = {
    [ProjectDeployResultNames.DEPLOY_TARGET]:
      'Kinotic could not choose where to run this deployment. This is a problem on Kinotic\'s side, not something you did.',
    [ProjectDeployResultNames.SBOM]:
      'Kinotic could not build the dependency inventory (SBOM) for this commit. This is a problem on Kinotic\'s side, not something you did.'
  }

  /** How the task's failure reads: a plain explanation, and whether the platform is at fault. */
  public static failureOf(node: JobTaskNode): JobTaskFailure {
    const platform = node.storedName ? ProjectDeployResultNames.PLATFORM_FAILURES[node.storedName] : undefined
    return platform
      ? { explanation: platform, platform: true }
      : { explanation: 'Open the step\'s log to see what went wrong.', platform: false }
  }

  /** The icon of the deployment step the task is, or undefined for a task outside the six steps. */
  public static iconOf(node: JobTaskNode): Component | undefined {
    return node.storedName ? ProjectDeployResultNames.ICONS[node.storedName] : undefined
  }

  /** The artifacts the task bound into the run, or null while the task has not completed. */
  public static artifactsOf(node: JobTaskNode): ProjectArtifacts | null {
    let ret: ProjectArtifacts | null = null
    if (ProjectDeployResultNames.hasArtifacts(node) && node.storedValue !== null && node.storedValue !== undefined) {
      ret = node.storedValue as ProjectArtifacts
    }
    return ret
  }

  /**
   * Whether the SBOM task generated the project's SBOM, false when the dependencies were unchanged,
   * or null for another task or while the task has not completed.
   */
  public static sbomGeneratedOf(node: JobTaskNode): boolean | null {
    let ret: boolean | null = null
    if (node.storedName === ProjectDeployResultNames.SBOM && typeof node.storedValue === 'boolean') {
      ret = node.storedValue
    }
    return ret
  }

  /** Whether the task's row lists the artifacts the deployed commit contains. */
  public static hasArtifacts(node: JobTaskNode): boolean {
    return node.storedName === ProjectDeployResultNames.ARTIFACTS
  }

  /** Whether the task's row has a detail pane: the artifacts it bound, or the workload log it ran. */
  public static hasDetail(node: JobTaskNode): boolean {
    return ProjectDeployResultNames.hasArtifacts(node) || ProjectDeployResultNames.hasWorkloadLog(node)
  }

  /**
   * Whether the task's row carries a workload log: the sync task's is the run's build log, the
   * publish task's is the upload log, the SBOM task's is the log of its generation.
   */
  public static hasWorkloadLog(node: JobTaskNode): boolean {
    return node.storedName === ProjectDeployResultNames.SYNC_WORKLOAD_ID
      || node.storedName === ProjectDeployResultNames.UI_DEPLOYMENTS
      || node.storedName === ProjectDeployResultNames.SBOM
  }

  /**
   * The workload whose log belongs to the task, or null while it is not yet known. The
   * workloads are named by the resolved deploy target before their tasks run; the sync task
   * also names its own once it completed.
   */
  public static workloadLogOf(node: JobTaskNode, root: JobTaskNode | null): string | null {
    let ret: string | null = null
    const target = root?.children
      .find(child => child.storedName === ProjectDeployResultNames.DEPLOY_TARGET)
      ?.storedValue as DeployTarget | undefined
    if (node.storedName === ProjectDeployResultNames.SYNC_WORKLOAD_ID) {
      ret = typeof node.storedValue === 'string' ? node.storedValue : target?.syncWorkloadId ?? null
    } else if (node.storedName === ProjectDeployResultNames.UI_DEPLOYMENTS) {
      ret = target?.uiPublishWorkloadId ?? null
    } else if (node.storedName === ProjectDeployResultNames.SBOM) {
      ret = target?.sbomWorkloadId ?? null
    }
    return ret
  }
}
