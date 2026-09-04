package org.kinotic.domain.api.model;

/**
 * The lifecycle states a {@link DeploymentStatus} carries. One set serves every deployed
 * thing the platform records: a project's deployment, a microservice's VM, a UI's site and
 * an organization's storage. Each uses the states that apply to it.
 */
public enum DeploymentStatusType {

    /**
     * A project's deployment job is running.
     */
    DEPLOYING,

    /**
     * An organization's storage or a UI's site is being created.
     */
    PROVISIONING,

    /**
     * A project's last deployment succeeded and its workloads run.
     */
    RUNNING,

    /**
     * A microservice's VM is up, running the artifact as of the commit the deployment was
     * last ensured for.
     */
    DEPLOYED,

    /**
     * An organization's storage, or a UI's site, is usable as of what the record says.
     */
    READY,

    /**
     * The last deployed commit no longer contains the microservice or UI. What was deployed
     * keeps running until the deployment is removed, and a commit that brings it back adopts
     * it.
     */
    ORPHANED,

    /**
     * The last attempt failed; the status message says why.
     */
    FAILED
}
