package org.kinotic.core.internal.api.secret;

import com.azure.core.exception.ResourceNotFoundException;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.security.keyvault.secrets.SecretAsyncClient;
import com.azure.security.keyvault.secrets.SecretClientBuilder;
import com.azure.security.keyvault.secrets.models.KeyVaultSecret;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.config.KinoticProperties;
import org.kinotic.core.api.secret.SecretReferenceResolver;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.concurrent.CompletableFuture;

/**
 * Resolves named secrets from the platform Azure Key Vault. Authenticates via
 * {@code DefaultAzureCredential} — on AKS this resolves to a Workload Identity, in
 * local dev to {@code az login} or env vars. Always reads the latest version of the
 * secret.
 *
 * <p>Active when {@code kinotic.secretStorage.azure.vaultUrl} is set and non-blank;
 * otherwise {@link EnvVarSecretReferenceResolver} fills the bean role. Both classes use
 * {@link ConditionalOnExpression} on the same property so activation is property-driven
 * and order-independent across the {@code @Component} scan.
 */
@Slf4j
@Component
@ConditionalOnExpression("!'${kinotic.secretStorage.azure.vaultUrl:}'.isBlank()")
public class AzureKeyVaultSecretReferenceResolver implements SecretReferenceResolver {

    private final SecretAsyncClient client;

    public AzureKeyVaultSecretReferenceResolver(KinoticProperties properties) {
        String vaultUrl = properties.getSecretStorage().getAzure().getVaultUrl();
        log.info("Resolving named secrets from Azure Key Vault at {}", vaultUrl);
        this.client = new SecretClientBuilder()
                .vaultUrl(vaultUrl)
                .credential(new DefaultAzureCredentialBuilder().build())
                .buildAsyncClient();
    }

    AzureKeyVaultSecretReferenceResolver(SecretAsyncClient client) {
        this.client = client;
    }

    @Override
    public CompletableFuture<String> resolve(String secretName) {
        if (secretName == null || secretName.isBlank()) {
            return CompletableFuture.completedFuture(null);
        }
        return client.getSecret(secretName)
                     .map(KeyVaultSecret::getValue)
                     .onErrorResume(ResourceNotFoundException.class, e -> {
                         log.debug("Secret '{}' not found in Key Vault", secretName);
                         return Mono.empty();
                     })
                     .toFuture();
    }
}
