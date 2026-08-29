import type { Identifiable } from '@kinotic-ai/core'
import type { ProjectDeploymentStatus } from '@/api/model/ProjectDeploymentStatus'

/**
 * Records where a Project's code is deployed: the node holding the checkout, the
 * long-lived workload serving it, and the commit currently live. One row per project;
 * the id equals the project id. Absence of a row means the project has never been
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
     * The id of the node hosting the project's checkout directory and runtime workload.
     */
    public nodeId: string | null = null

    /**
     * Absolute path on the node of the host directory holding the project's checkout.
     */
    public hostDir: string | null = null

    /**
     * The id of the long-lived workload running the project's microservices, or null
     * while the first deployment is still in progress.
     */
    public runtimeWorkloadId: string | null = null

    /**
     * Sha of the last commit successfully synced to the node.
     */
    public commitSha: string | null = null

    /**
     * The id of the most recent deployment job run for this project.
     */
    public lastJobRunId: string | null = null

    public status!: ProjectDeploymentStatus

    public created: number | null = null

    public updated: number | null = null

}
