package org.kinotic.core.internal.api.secret;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.secret.SecretReferenceResolver;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Dev fallback for {@link SecretReferenceResolver}. Resolves secrets from process env
 * vars named {@code KINOTIC_AKV_<sanitizedSecretName>} — the secret name is uppercased
 * with non-alphanumeric characters replaced by {@code _}, matching standard env-var
 * conventions. Returns {@code null} when the env var is not set.
 *
 * <p>Active when {@code kinotic.secretStorage.azure.vaultUrl} is unset or blank.
 * Mutually exclusive with {@link AzureKeyVaultSecretReferenceResolver} via the same
 * property — both classes use {@link ConditionalOnExpression} rather than
 * {@code @ConditionalOnMissingBean} so activation is property-driven and order-independent
 * across the {@code @Component} scan.
 */
@Slf4j
@Component
@ConditionalOnExpression("'${kinotic.secretStorage.azure.vaultUrl:}'.isBlank()")
public class EnvVarSecretReferenceResolver implements SecretReferenceResolver {

    @PostConstruct
    void announce() {
        log.info("Resolving named secrets from KINOTIC_AKV_* env vars");
    }

    @Override
    public CompletableFuture<String> resolve(String secretName) {
        if (secretName == null || secretName.isBlank()) {
            return CompletableFuture.completedFuture(null);
        }
        String envName = "KINOTIC_AKV_" + secretName.replaceAll("[^a-zA-Z0-9]", "_").toUpperCase();
        String value = System.getenv(envName);
        if (value == null) {
            log.debug("Secret '{}' not found in env var {}", secretName, envName);
        }
        return CompletableFuture.completedFuture(value);
    }
}
