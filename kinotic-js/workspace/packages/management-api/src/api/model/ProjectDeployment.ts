import type { Identifiable } from '@kinotic-ai/core'
import type { ProjectArtifacts } from '@/api/model/ProjectArtifacts'
import type { DeploymentStatus } from '@/api/model/DeploymentStatus'

/**
 * Records where a Project's code is deployed: the node holding the checkout, the sync workload
 * and identity of its deployments, the artifacts of the synced commit, and the commit currently
 * live. The microservices themselves are recorded one per MicroserviceDeployment. One row per
 * project; the id equals the project id. Absence of a row means the project has never been
 * deployed.
 */
export class ProjectDeployment implements Identifiable<string> {

    /**
     * The id of the deployment, always equal to the id of the deployed project.
     */
    public id: string | null = null

    public organizationId!: string

    public applicationId!: string

    /**
     * The id of the node hosting the project's checkout directory and every workload of its
     * deployments.
     */
    public nodeId: string | null = null

    /**
     * Absolute path on the node of the host directory holding the project's checkout.
     */
    public hostDir: string | null = null

    /**
     * The id of the sync workload of the most recent deployment run, kept with its logs until
     * the next run retires it, or null before the first run resolved its target.
     */
    public syncWorkloadId: string | null = null

    /**
     * The id of the UI publish workload of the most recent deployment run, kept with its logs
     * until the next run retires it, or null before a run has published a UI.
     */
    public uiPublishWorkloadId: string | null = null

    /**
     * The id of the machine identity the sync workload authenticates as, or null before the
     * project's first deployment. Its secret is reissued for every deployment.
     */
    public syncMachineIdentityId: string | null = null

    /**
     * Sha of the last commit successfully synced to the node.
     */
    public commitSha: string | null = null

    /**
     * The artifacts the sync workload found in the checkout of artifactsCommitSha, or null
     * before a sync has reported any.
     */
    public artifacts: ProjectArtifacts | null = null

    /**
     * Sha of the commit artifacts were found in.
     */
    public artifactsCommitSha: string | null = null

    /**
     * The id of the most recent deployment job run for this project.
     */
    public lastJobRunId: string | null = null

    public status!: DeploymentStatus

    public created: number | null = null

    public updated: number | null = null

}
