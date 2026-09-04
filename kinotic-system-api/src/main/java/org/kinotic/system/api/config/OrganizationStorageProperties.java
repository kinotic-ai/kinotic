package org.kinotic.system.api.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Where the platform provisions each organization's storage account, bound under
 * {@code kinotic.systemApi.organizationStorage.*}. In production the Azure fields name
 * the subscriptions, resource group and network the accounts are created in; in development
 * the provisioner is disabled and every organization is pointed at one Azurite. The Azure
 * fields are validated at boot, so an environment that disables the provisioner still sets
 * them, to placeholders, as it does the GitHub App settings.
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
     * When true no private endpoint is created and the platform reaches each account over
     * its public endpoint, as a server outside the platform VNet, such as a developer machine,
     * must. {@link #privateEndpointSubnetId} and {@link #privateDnsZoneId} are then unused.
     */
    private boolean disablePrivateEndpoint = false;

    /**
     * The Azure subscriptions organization storage accounts are spread over. An organization's
     * account is created in one of them and stays there.
     */
    @NotEmpty
    private List<String> subscriptionIds = new ArrayList<>();

    /**
     * The resource group, present in every listed subscription, that holds the storage
     * accounts and their private endpoints.
     */
    @NotBlank
    private String resourceGroup;

    /**
     * The Azure region the storage accounts are created in.
     */
    @NotBlank
    private String location;

    /**
     * Id of the subnet in the platform VNet that each account's private endpoint is placed in.
     * Required unless {@link #disablePrivateEndpoint} is true.
     */
    private String privateEndpointSubnetId;

    /**
     * Id of the {@code privatelink.blob.core.windows.net} private DNS zone each account is
     * registered in, linked to the platform VNet. Required unless
     * {@link #disablePrivateEndpoint} is true.
     */
    private String privateDnsZoneId;

    /**
     * Connection string of the Azurite the mock provisioner points every organization at.
     */
    private String azuriteConnectionString;

    @AssertTrue(message = "privateEndpointSubnetId and privateDnsZoneId are required unless disablePrivateEndpoint is true")
    public boolean isPrivateEndpointConfigured() {
        return disablePrivateEndpoint
                || (StringUtils.isNotBlank(privateEndpointSubnetId) && StringUtils.isNotBlank(privateDnsZoneId));
    }

}
