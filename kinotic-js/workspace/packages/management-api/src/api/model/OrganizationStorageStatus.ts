import { OrganizationStorageStatusType } from '@/api/model/OrganizationStorageStatusType'

/**
 * Lifecycle state of an organization's storage, and why when something is wrong.
 */
export interface OrganizationStorageStatus {
    /**
     * The lifecycle state of the storage.
     */
    type: OrganizationStorageStatusType
    /**
     * Why the storage is in this state, or null when the state speaks for itself;
     * typically the failure that left it FAILED.
     */
    message: string | null
}
