package org.kinotic.core.api.secret;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Azure Key Vault configuration settings.
 */
@Getter
@Setter
@Accessors(chain = true)
public class AzureSettings {
    private String vaultUrl;
}
