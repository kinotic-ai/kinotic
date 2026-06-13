package org.kinotic.core.api.secret;

import java.util.concurrent.CompletableFuture;

/**
 * Resolves a named secret from external storage (typically Azure Key Vault) by its
 * known name. Distinct from {@link SecretStorageService} — that service stores secrets
 * Kinotic itself owns under HKDF-derived opaque names, while this resolver fetches
 * secrets that an operator put in the vault under human-readable names (OIDC client
 * secrets, third-party API keys, etc.) and Kinotic only reads.
 *
 * <p>Always returns the latest version of the secret. The vault URI is global config
 * — only the secret name varies per call. Returns {@code null} when the secret does
 * not exist; propagates network / authorization errors so the caller can surface the
 * failure rather than silently treating misconfiguration as missing data.
 */
public interface SecretReferenceResolver {

    CompletableFuture<String> resolve(String secretName);
}
