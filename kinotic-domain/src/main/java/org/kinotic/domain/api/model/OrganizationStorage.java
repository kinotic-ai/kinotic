package org.kinotic.domain.api.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * An organization's storage: the one account everything its deployments publish goes to,
 * and where that account is in its lifecycle. Held by {@link Organization} once a deployment
 * has first needed it; the account's details fill in as provisioning progresses.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class OrganizationStorage {

    /**
     * The Azure subscription holding the account, or {@code null} when it is not hosted in
     * Azure.
     */
    private String subscriptionId;

    /**
     * The name of the account.
     */
    private String accountName;

    /**
     * The blob service endpoint of the account, or {@code null} until it exists.
     */
    private String blobEndpoint;

    /**
     * The address the account answers on inside the platform network, which is the one
     * destination a publish workload may reach, or {@code null} until it exists.
     */
    private String privateEndpointIp;

    /**
     * Where the storage is in its lifecycle.
     */
    private DeploymentStatus status;

}
