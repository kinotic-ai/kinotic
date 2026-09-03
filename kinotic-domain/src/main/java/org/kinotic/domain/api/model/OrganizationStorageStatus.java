package org.kinotic.domain.api.model;

/**
 * Lifecycle state of an organization's storage, and why when something is wrong.
 *
 * @param type    the lifecycle state of the storage
 * @param message why the storage is in this state, or null when the state speaks for
 *                itself; typically the failure that left it
 *                {@link OrganizationStorageStatusType#FAILED}
 */
public record OrganizationStorageStatus(OrganizationStorageStatusType type, String message) {

    public OrganizationStorageStatus(OrganizationStorageStatusType type) {
        this(type, null);
    }
}
