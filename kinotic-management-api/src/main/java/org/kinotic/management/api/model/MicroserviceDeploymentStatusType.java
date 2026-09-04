package org.kinotic.management.api.model;

/**
 * The lifecycle states of a {@link MicroserviceDeployment}, carried by its
 * {@link MicroserviceDeploymentStatus}.
 */
public enum MicroserviceDeploymentStatusType {

    /**
     * The microservice's VM is up, running the artifact as of the commit the deployment was
     * last ensured for.
     */
    DEPLOYED,

    /**
     * The last deployment could not leave the microservice running; the status message says
     * why.
     */
    FAILED,

    /**
     * The last deployed commit no longer contains the microservice. Its VM keeps running
     * until the deployment is removed, and a commit that brings the microservice back adopts
     * it.
     */
    ORPHANED
}
