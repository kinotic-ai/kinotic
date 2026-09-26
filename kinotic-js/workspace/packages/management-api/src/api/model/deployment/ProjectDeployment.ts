import type { Reconcilable } from '@/api/model/reconcile/Reconcilable'
import { ReconcileState } from '@/api/model/reconcile/ReconcileState'
import type { ProjectArtifacts } from '@/api/model/deployment/ProjectArtifacts'
import type { DeploymentState } from '@/api/model/deployment/DeploymentState'

/**
 * Records where a Project's code is deployed: the node holding the checkout, the sync workload
 * and identity of its deployments, the artifacts of the synced commit, and what it should be
 * beside what it is. The microservices themselves are recorded one per MicroserviceDeployment. One row per
 * project; the id equals the project id. Absence of a row means the project has never been
 * deployed.
 */
export class ProjectDeployment implements Reconcilable<DeploymentState> {

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
     * The id of the sync workload of the most recent deployment run, or null before the first run
     * resolved its target. The workload is destroyed when its run ends; its logs stay in the
     * organization's log store under this id.
     */
    public syncWorkloadId: string | null = null

    /**
     * The id of the UI publish workload of the most recent deployment run, or null before a run
     * has published a UI. The workload is destroyed when its run ends; its logs stay in the
     * organization's log store under this id.
     */
    public uiPublishWorkloadId: string | null = null

    /**
     * The id of the SBOM workload of the most recent deployment run, or null before a run has
     * generated an SBOM. The workload is destroyed when its run ends; its logs stay in the
     * organization's log store under this id.
     */
    public sbomWorkloadId: string | null = null

    /**
     * The id of the machine identity the sync workload authenticates as, or null before the
     * project's first deployment. Its secret is reissued for every deployment.
     */
    public syncMachineIdentityId: string | null = null

    /**
     * The artifacts the sync workload found in the checkout of the commit it last synced, or null
     * before a sync has reported any.
     */
    public artifacts: ProjectArtifacts | null = null

    /**
     * The id of the most recent deployment job run for this project.
     */
    public lastJobRunId: string | null = null

    /**
     * Why the last deployment failed, or null when it did not.
     */
    public failureMessage: string | null = null

    /**
     * What the deployment should be, the commit its last qualifying push asked for, beside what it
     * is, the phase it is in and the commit it serves, with what the platform keeps on every
     * watched record.
     */
    public state: ReconcileState<DeploymentState> = new ReconcileState()

    public created: number | null = null

    public updated: number | null = null

}
