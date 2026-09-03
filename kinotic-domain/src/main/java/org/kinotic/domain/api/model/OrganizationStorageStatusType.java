package org.kinotic.domain.api.model;

/**
 * The lifecycle states of an organization's storage, carried by its
 * {@link OrganizationStorageStatus}.
 */
public enum OrganizationStorageStatusType {

    /**
     * The storage account and its private endpoint are being created; a deployment that
     * needs the storage waits for it.
     */
    PROVISIONING,

    /**
     * The storage exists and the organization's deployments can publish to it.
     */
    READY,

    /**
     * Provisioning did not complete; the status message says why. The next deployment that
     * needs the storage tries again.
     */
    FAILED
}
