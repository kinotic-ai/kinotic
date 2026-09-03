package org.kinotic.management.api.config;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/**
 * Where the platform provisions each organization's storage account, bound under
 * {@code kinotic.managementApi.organizationStorage.*}. In production the Azure fields name
 * the subscriptions, resource group and network the accounts are created in; in development
 * the provisioner is disabled and every organization is pointed at one Azurite.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class OrganizationStorageProperties {

    /**
     * When true the Azure provisioner is not registered and
     * {@code MockOrganizationStorageProvisioner} points every organization at
     * {@link #azuriteConnectionString} instead.
     */
    private boolean disableProvisioner = false;

    /**
     * The Azure subscriptions organization storage accounts are spread over. An organization's
     * account is created in one of them and stays there.
     */
    private List<String> subscriptionIds = new ArrayList<>();

    /**
     * The resource group, present in every listed subscription, that holds the storage
     * accounts and their private endpoints.
     */
    private String resourceGroup;

    /**
     * The Azure region the storage accounts are created in.
     */
    private String location;

    /**
     * Id of the subnet in the platform VNet that each account's private endpoint is placed in.
     */
    private String privateEndpointSubnetId;

    /**
     * Id of the {@code privatelink.blob.core.windows.net} private DNS zone each account is
     * registered in, linked to the platform VNet.
     */
    private String privateDnsZoneId;

    /**
     * Connection string of the Azurite the mock provisioner points every organization at.
     */
    private String azuriteConnectionString;

}
