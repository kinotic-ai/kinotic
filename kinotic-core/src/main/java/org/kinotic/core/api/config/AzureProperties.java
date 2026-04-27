package org.kinotic.core.api.config;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Azure Key Vault configuration settings.
 */
@Getter
@Setter
@Accessors(chain = true)
public class AzureProperties {
    /**
     * The full URL of the Azure Key Vault instance (e.g. {@code https://my-vault.vault.azure.net/}).
     */
    private String vaultUrl;
}
