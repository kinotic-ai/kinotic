import type { Identifiable } from '@kinotic-ai/core'
import type { MicroserviceDeploymentStatus } from '@/api/model/MicroserviceDeploymentStatus'

/**
 * The standing deployment of one microservice artifact of a Project: the VM running it, the
 * machine identity that VM connects as, the commit it was last ensured for, and its status.
 * One row per microservice a deployment has ensured; a row outlives the artifact until the
 * deployment is removed.
 */
export class MicroserviceDeployment implements Identifiable<string> {

    /**
     * Unique id of the deployment.
     */
    public id: string | null = null

    public organizationId!: string

    public applicationId!: string

    /**
     * The id of the project the microservice belongs to.
     */
    public projectId!: string

    /**
     * The microservice's identity: the MicroserviceArtifact name it was deployed from. Unique
     * among the project's microservice deployments.
     */
    public name!: string

    /**
     * The id of the workload running the microservice, or null when the deployment could not
     * create one.
     */
    public workloadId: string | null = null

    /**
     * The id of the machine identity the microservice's workload authenticates as. Its secret
     * is issued once, with the workload it belongs to.
     */
    public machineIdentityId: string | null = null

    /**
     * The module the workload was started with, relative to the checkout root: the artifact's
     * directory joined with its entry. A commit that moves the entry point replaces the
     * workload.
     */
    public entryPoint: string | null = null

    /**
     * Sha of the commit the deployment was last ensured for.
     */
    public commitSha: string | null = null

    public status!: MicroserviceDeploymentStatus

    public created: number | null = null

    public updated: number | null = null

}
