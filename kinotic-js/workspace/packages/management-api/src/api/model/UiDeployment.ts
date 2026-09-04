import type { Identifiable } from '@kinotic-ai/core'
import type { DeploymentStatus } from '@/api/model/DeploymentStatus'

/**
 * The standing deployment of one UI artifact of a Project: the site serving it, the commit
 * it serves, and its status. One row per UI a deployment has published; a row outlives the
 * artifact until the deployment is removed.
 */
export class UiDeployment implements Identifiable<string> {

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
     * Sha of the commit the site serves, or null until the first publish completes.
     */
    public commitSha: string | null = null

    public status!: DeploymentStatus

    public created: number | null = null

    public updated: number | null = null

}
