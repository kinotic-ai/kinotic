/**
 * The lifecycle states of a MicroserviceDeployment, carried by its MicroserviceDeploymentStatus.
 */
export enum MicroserviceDeploymentStatusType {
    /**
     * The microservice's VM is up, running the artifact as of the commit the deployment was
     * last ensured for.
     */
    DEPLOYED = 'DEPLOYED',
    /**
     * The last deployment could not leave the microservice running; the status message says
     * why.
     */
    FAILED = 'FAILED',
    /**
     * The last deployed commit no longer contains the microservice. Its VM keeps running until
     * the deployment is removed, and a commit that brings the microservice back adopts it.
     */
    ORPHANED = 'ORPHANED'
}
