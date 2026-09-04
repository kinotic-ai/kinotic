/**
 * The lifecycle states of a UiDeployment, carried by its UiDeploymentStatus.
 */
export enum UiDeploymentStatusType {
    /**
     * The site is being created: its hostname is registered and its certificate issued. The
     * files are published meanwhile, so the site serves as soon as the hostname does.
     */
    PROVISIONING = 'PROVISIONING',
    /**
     * The site serves the UI as of the commit the deployment records.
     */
    READY = 'READY',
    /**
     * The last deployed commit no longer contains the UI. The site keeps serving the last
     * published commit until the deployment is removed, and a commit that brings the UI back
     * adopts it.
     */
    ORPHANED = 'ORPHANED',
    /**
     * The site could not be created; the status message says why. Provisioning can be
     * retried.
     */
    FAILED = 'FAILED'
}
