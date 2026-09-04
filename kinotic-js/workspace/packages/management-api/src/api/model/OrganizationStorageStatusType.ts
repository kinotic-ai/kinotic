/**
 * The lifecycle states of an organization's storage, carried by its OrganizationStorageStatus.
 */
export enum OrganizationStorageStatusType {
    /**
     * The storage account and what the platform reaches it through are being created.
     */
    PROVISIONING = 'PROVISIONING',
    /**
     * The storage is usable: deployments publish to it.
     */
    READY = 'READY',
    /**
     * Provisioning failed; the status message says why, and it can be run again.
     */
    FAILED = 'FAILED'
}
