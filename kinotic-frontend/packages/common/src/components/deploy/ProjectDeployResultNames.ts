import { TaskViews } from '@kinotic-ai/management-api'
import type { JobTaskFailure } from '../grind/JobTaskFailure'
import type { JobTaskNode } from '../grind/JobTaskNode'

/**
 * How a project deployment run's failures read on the job page: which steps fail on the platform's
 * side rather than the project's, and how to start another run.
 */
export default class ProjectDeployResultNames {

  /** The name the run's first task stores its DeployTarget under, mirroring ProjectDeployResultNames on the server. */
  public static readonly DEPLOY_TARGET = 'deployTarget'

  /** How to start another deployment run, for a run that failed. */
  public static readonly RETRY_HINT = 'To try again, push a commit to the project\'s default branch; each push starts a new deployment run.'

  private static readonly TARGET_FAILURE =
    'Kinotic could not choose where to run this deployment. This is a problem on Kinotic\'s side, not something you did.'

  private static readonly SBOM_FAILURE =
    'Kinotic could not build the dependency inventory (SBOM) for this commit. This is a problem on Kinotic\'s side, not something you did.'

  /** How the task's failure reads: a plain explanation, and whether the platform is at fault. */
  public static failureOf(node: JobTaskNode): JobTaskFailure {
    let ret: JobTaskFailure
    if (node.storedName === ProjectDeployResultNames.DEPLOY_TARGET) {
      ret = { explanation: ProjectDeployResultNames.TARGET_FAILURE, platform: true }
    } else if (node.view?.kind === TaskViews.SBOM_GENERATION) {
      ret = { explanation: ProjectDeployResultNames.SBOM_FAILURE, platform: true }
    } else {
      ret = { explanation: 'Open the step\'s log to see what went wrong.', platform: false }
    }
    return ret
  }
}
