package org.kinotic.domain.internal.api.services.secret;

import com.azure.core.exception.ResourceNotFoundException;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.security.keyvault.secrets.SecretAsyncClient;
import com.azure.security.keyvault.secrets.SecretClientBuilder;
import com.azure.security.keyvault.secrets.models.KeyVaultSecret;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.domain.api.config.KinoticDomainProperties;
import org.kinotic.core.api.secret.SecretReferenceResolver;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Resolves named secrets from the platform Azure Key Vault. Authenticates via
 * {@code DefaultAzureCredential} — on AKS this resolves to a Workload Identity, in
 * local dev to {@code az login} or env vars. Always reads the latest version of the
 * secret.
 *
 * <p>Active when {@code kinotic.domain.secretStorage.azure.vaultUrl} is set and non-blank;
 * otherwise {@link EnvVarSecretReferenceResolver} fills the bean role. Both classes use
 * {@link ConditionalOnExpression} on the same property so activation is property-driven
 * and order-independent across the {@code @Component} scan.
 */
@Slf4j
@Component
@ConditionalOnExpression("!'${kinotic.domain.secretStorage.azure.vaultUrl:}'.isBlank()")
public class AzureKeyVaultSecretReferenceResolver implements SecretReferenceResolver {

    private final SecretAsyncClient client;
    private final Vertx vertx;

    public AzureKeyVaultSecretReferenceResolver(KinoticDomainProperties properties, Vertx vertx) {
        String vaultUrl = properties.getDomain().getSecretStorage().getAzure().getVaultUrl();
        log.info("Resolving named secrets from Azure Key Vault at {}", vaultUrl);
        this.client = new SecretClientBuilder()
                .vaultUrl(vaultUrl)
                .credential(new DefaultAzureCredentialBuilder().build())
                .buildAsyncClient();
        this.vertx = vertx;
    }

    @Override
    public Future<String> resolve(String secretName) {
        if (secretName == null || secretName.isBlank()) {
            return Future.succeededFuture();
        }
        return Future.fromCompletionStage(client.getSecret(secretName)
                                                .map(KeyVaultSecret::getValue)
                                                .onErrorResume(ResourceNotFoundException.class, e -> {
                                                    log.debug("Secret '{}' not found in Key Vault", secretName);
                                                    return Mono.empty();
                                                })
                                                .toFuture(),
                                          vertx.getOrCreateContext());
    }
}
