import type { DeploymentStatus } from '@/api/model/DeploymentStatus'

/**
 * An organization's storage: the one Azure storage account everything its deployments publish
 * goes to, and where that account is in its lifecycle. The account's details fill in as
 * provisioning progresses. In development an Azurite stands in for the account and fills the
 * same fields.
 */
export interface OrganizationStorage {
    /**
     * The Azure subscription holding the account, or null when an Azurite stands in for it.
     */
    azureSubscriptionId: string | null
    /**
     * The name of the storage account.
     */
    azureAccountName: string | null
    /**
     * The blob service endpoint of the account, https://<account>.blob.core.windows.net/, or
     * null until it exists. Its hostname is where workloads reach the account.
     */
    azureBlobEndpoint: string | null
    /**
     * Where the storage is in its lifecycle.
     */
    status: DeploymentStatus
}
