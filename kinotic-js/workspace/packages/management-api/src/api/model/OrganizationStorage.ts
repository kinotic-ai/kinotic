import type { DeploymentStatus } from '@/api/model/DeploymentStatus'

/**
 * An organization's storage: the one account everything its deployments publish goes to,
 * and where that account is in its lifecycle. The account's details fill in as provisioning
 * progresses.
 */
export interface OrganizationStorage {
    /**
     * The Azure subscription holding the account, or null when it is not hosted in Azure.
     */
    subscriptionId: string | null
    /**
     * The name of the account.
     */
    accountName: string | null
    /**
     * The blob service endpoint of the account, or null until it exists.
     */
    blobEndpoint: string | null
    /**
     * The one host a publish workload may reach, where the account answers the platform: its
     * private endpoint's address inside the platform network, or its public host where no
     * private endpoint exists. Null until the account exists.
     */
    publishHost: string | null
    /**
     * Where the storage is in its lifecycle.
     */
    status: DeploymentStatus
}
