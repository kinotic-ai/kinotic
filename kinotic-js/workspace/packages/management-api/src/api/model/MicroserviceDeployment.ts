import type { Reconcilable } from '@/api/reconcile/Reconcilable'
import { ReconcileState } from '@/api/reconcile/ReconcileState'
import type { DeploymentState } from '@/api/model/DeploymentState'

/**
 * The standing deployment of one microservice artifact of a Project: the VM running it, the
 * machine identity that VM connects as, and what the deployment should be beside what it is.
 * One row per microservice a deployment has ensured; a row outlives the artifact until the
 * deployment is removed.
 */
export class MicroserviceDeployment implements Reconcilable<DeploymentState> {

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
     * The id of the workload running the microservice, or null while none has been created.
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
     * Why the microservice is not running as it should, or null when it is: the failure of its
     * last deployment, or the exit of a VM that is being started again.
     */
    public failureMessage: string | null = null

    /**
     * What the deployment should be, the commit its project's last deployment asked it to run,
     * beside what it is, the phase it is in and the commit it serves, with what the platform keeps
     * on every watched record: that the node running it cannot be reached, and the project
     * deployment it belongs to.
     */
    public state: ReconcileState<DeploymentState> = new ReconcileState()

    public created: number | null = null

    public updated: number | null = null

}
