package org.kinotic.domain.internal.api.services.secret;

import io.vertx.core.Future;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.secret.SecretReferenceResolver;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;
import org.springframework.stereotype.Component;

/**
 * Dev fallback for {@link SecretReferenceResolver}. Resolves secrets from the Spring
 * {@link Environment} property {@code KINOTIC_AKV_<sanitizedSecretName>} — the secret name is
 * uppercased with non-alphanumeric characters replaced by {@code _}, matching standard env-var
 * conventions — so a secret can be an environment variable or a key in any loaded config file.
 * Returns {@code null} when the property is not set.
 *
 * <p>Active when {@code kinotic.domain.secretStorage.azure.vaultUrl} is unset or blank.
 * Mutually exclusive with {@link AzureKeyVaultSecretReferenceResolver} via the same
 * property — both classes use {@link ConditionalOnExpression} rather than
 * {@code @ConditionalOnMissingBean} so activation is property-driven and order-independent
 * across the {@code @Component} scan.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnExpression("'${kinotic.domain.secretStorage.azure.vaultUrl:}'.isBlank()")
public class EnvVarSecretReferenceResolver implements SecretReferenceResolver {

    private final ConfigurableEnvironment environment;

    @PostConstruct
    void announce() {
        log.info("Resolving named secrets from KINOTIC_AKV_* properties");
    }

    @Override
    public Future<String> resolve(String secretName) {
        if (secretName == null || secretName.isBlank()) {
            return Future.succeededFuture();
        }
        String propertyName = "KINOTIC_AKV_" + secretName.replaceAll("[^a-zA-Z0-9]", "_").toUpperCase();
        // Each source's raw value: Environment.getProperty would resolve ${...} inside a secret
        String value = null;
        for (PropertySource<?> source : environment.getPropertySources()) {
            Object raw = source.getProperty(propertyName);
            if (raw != null) {
                value = raw.toString();
                break;
            }
        }
        if (value == null) {
            log.debug("Secret '{}' not found in property {}", secretName, propertyName);
        }
        return Future.succeededFuture(value);
    }
}
