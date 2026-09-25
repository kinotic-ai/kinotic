import type { Reconcilable } from '@/api/model/Reconcilable'
import { ReconcileState } from '@/api/model/ReconcileState'
import type { DeploymentState } from '@/api/model/DeploymentState'

/**
 * The standing deployment of one UI artifact of a Project: the site serving it, and what the
 * deployment should be beside what it is. One row per UI a deployment has published; a row outlives the
 * artifact until the deployment is removed.
 */
export class UiDeployment implements Reconcilable<DeploymentState> {

    /**
     * The site's hostname label under the platform's sites domain, minted once when the UI
     * is first published: `<org>-<app>-<ui>`, with a numeric suffix when that label is taken.
     */
    public id: string | null = null

    public organizationId!: string

    public applicationId!: string

    /**
     * The id of the project the UI belongs to.
     */
    public projectId!: string

    /**
     * The UI's identity: the UiArtifact name it was published from. Unique among the
     * project's UI deployments.
     */
    public name!: string

    /**
     * Where the site is served, `https://<id>.<sites domain>`, fixed when the label is minted.
     */
    public url!: string

    /**
     * Why the site does not yet serve what it should, as last observed, or null when it does.
     */
    public failureMessage: string | null = null

    /**
     * What the deployment should be, the commit its project's last deployment published to the
     * site, beside what it is, the phase it is in and the commit the site serves, with what the
     * platform keeps on every watched record: the project deployment it belongs to.
     */
    public state: ReconcileState<DeploymentState> = new ReconcileState()

    public created: number | null = null

    public updated: number | null = null

}
