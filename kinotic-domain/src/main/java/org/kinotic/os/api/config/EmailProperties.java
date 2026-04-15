package org.kinotic.os.api.config;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.time.Duration;

/**
 * Configuration for outbound email delivered via Azure Communication Services.
 * Bound under the {@code kinotic.email} prefix via {@link KinoticDomainProperties}.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class EmailProperties {

    /**
     * When {@code false}, {@code EmailService} will log the verification URL instead of sending.
     * Useful for local development without an Azure login.
     */
    private boolean enabled = true;

    /**
     * ACS endpoint URL, e.g. {@code https://my-acs-resource.communication.azure.com}.
     * Required when {@link #enabled} is {@code true}.
     */
    private String endpoint;

    /**
     * Verified ACS domain sender address, e.g. {@code DoNotReply@xyz.azurecomm.net}.
     */
    private String senderAddress;

    /**
     * Public base URL of the application, used to build absolute verification links.
     */
    private String appBaseUrl = "http://localhost:9090";

    /**
     * Maximum time to wait for the ACS send operation to complete.
     */
    private Duration sendTimeout = Duration.ofSeconds(30);

    /**
     * Optional client id of a User-Assigned Managed Identity, forwarded to
     * {@code DefaultAzureCredentialBuilder.managedIdentityClientId(...)}. When null,
     * {@code DefaultAzureCredential} auto-resolves via AKS Workload-Identity env vars
     * ({@code AZURE_CLIENT_ID}, {@code AZURE_TENANT_ID}, {@code AZURE_FEDERATED_TOKEN_FILE}).
     */
    private String managedIdentityClientId;

}
