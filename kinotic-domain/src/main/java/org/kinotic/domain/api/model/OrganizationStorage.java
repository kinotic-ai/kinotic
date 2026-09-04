package org.kinotic.domain.api.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * An organization's storage: the one Azure storage account everything its deployments publish
 * goes to, and where that account is in its lifecycle. Held by {@link Organization} once its
 * provisioning job has started; the account's details fill in as provisioning progresses. In
 * development an Azurite stands in for the account and fills the same fields.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class OrganizationStorage {

    /**
     * The Azure subscription holding the account, or {@code null} when an Azurite stands in
     * for it.
     */
    private String azureSubscriptionId;

    /**
     * The name of the storage account.
     */
    private String azureAccountName;

    /**
     * The blob service endpoint of the account, {@code https://<account>.blob.core.windows.net/},
     * or {@code null} until it exists. Its hostname is where workloads reach the account.
     */
    private String azureBlobEndpoint;

    /**
     * Where the storage is in its lifecycle.
     */
    private DeploymentStatus status;

}
