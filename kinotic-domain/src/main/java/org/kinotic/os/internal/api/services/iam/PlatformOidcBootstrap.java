package org.kinotic.os.internal.api.services.iam;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.secret.SecretStorageService;
import org.kinotic.os.api.config.KinoticDomainProperties;
import org.kinotic.os.api.config.PlatformOidcProperties;
import org.kinotic.os.api.model.KinoticSystem;
import org.kinotic.os.api.model.iam.OidcConfiguration;
import org.kinotic.os.api.services.KinoticSystemService;
import org.kinotic.os.api.services.iam.OidcConfigurationService;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;

/**
 * Reconciles the platform-wide OIDC providers declared in
 * {@link PlatformOidcProperties#getPlatformProviders()} into Elasticsearch on startup.
 * <p>
 * For each entry: reads the matching client-secret file from
 * {@link PlatformOidcProperties#getSecretsPath()}, stores it via
 * {@link SecretStorageService} under scope=configId / key="clientSecret", upserts the
 * {@link OidcConfiguration} entity, and ensures the system entity references it.
 * Idempotent — restarts re-apply, drift gets corrected. Skips entries whose secret file
 * is missing (logged warning) so a partially-configured deployment still boots.
 * <p>
 * For org-level SSO configs (per-tenant Okta/Entra/etc.) the admin UI does the equivalent
 * via the published {@link OidcConfigurationService}; this bootstrap is platform-only.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlatformOidcBootstrap {

    /** Constant secret-storage key — each platform config's client secret lives at scope=configId. */
    private static final String CLIENT_SECRET_REF = "clientSecret";

    private final KinoticDomainProperties properties;
    private final OidcConfigurationService oidcConfigurationService;
    private final KinoticSystemService kinoticSystemService;
    private final SecretStorageService secretStorageService;

    @PostConstruct
    public void start() {
        PlatformOidcProperties oidc = properties.getDomain().getOidc();
        if (oidc == null || oidc.getPlatformProviders() == null || oidc.getPlatformProviders().isEmpty()) {
            log.info("No platform OIDC providers configured; skipping bootstrap");
            return;
        }

        // Best-effort: log and continue on failures rather than crashing the boot. Operators
        // see warnings, fix config or the missing file, and restart.
        for (PlatformOidcProperties.PlatformProviderEntry entry : oidc.getPlatformProviders()) {
            try {
                reconcile(entry, oidc.getSecretsPath());
            } catch (Exception e) {
                log.error("Failed to reconcile platform OIDC provider {}: {}",
                          entry == null ? "<null>" : entry.getId(), e.getMessage(), e);
            }
        }
    }

    private void reconcile(PlatformOidcProperties.PlatformProviderEntry entry, Path secretsRoot) {
        if (entry.getId() == null || entry.getId().isBlank()
                || entry.getProvider() == null || entry.getClientId() == null
                || entry.getAuthority() == null) {
            log.warn("Platform OIDC entry skipped — missing id/provider/clientId/authority: {}", entry);
            return;
        }

        Path secretFile = secretsRoot.resolve(entry.getId());
        String clientSecret = readSecretFile(secretFile, entry.getId());
        if (clientSecret == null) return;

        // Store under (scope=configId, key="clientSecret"). OAuth2AuthRegistry resolves it the same way.
        secretStorageService.setSecret(entry.getId(), CLIENT_SECRET_REF, clientSecret).join();

        OidcConfiguration desired = new OidcConfiguration()
                .setId(entry.getId())
                .setName(entry.getName() != null ? entry.getName() : entry.getId())
                .setProvider(entry.getProvider())
                .setClientId(entry.getClientId())
                .setClientSecretRef(CLIENT_SECRET_REF)
                .setAuthority(entry.getAuthority())
                .setAudience(entry.getAudience() != null ? entry.getAudience() : entry.getClientId())
                .setEnabled(true);

        OidcConfiguration existing = oidcConfigurationService.findById(entry.getId()).join();
        if (existing == null) {
            desired.setCreated(new Date());
            oidcConfigurationService.save(desired).join();
            log.info("Created platform OIDC config {} ({})", entry.getId(), entry.getName());
        } else {
            // Preserve created timestamp + carry forward any admin-only fields not in the bootstrap entry.
            desired.setCreated(existing.getCreated());
            desired.setRolesClaimPath(existing.getRolesClaimPath());
            desired.setAdditionalScopes(existing.getAdditionalScopes());
            desired.setProvisioningMode(existing.getProvisioningMode());
            desired.setBackChannelAuthority(existing.getBackChannelAuthority());
            desired.setRedirectUri(existing.getRedirectUri());
            desired.setPostLogoutRedirectUri(existing.getPostLogoutRedirectUri());
            desired.setSilentRedirectUri(existing.getSilentRedirectUri());
            desired.setDomains(existing.getDomains());
            oidcConfigurationService.save(desired).join();
            log.info("Updated platform OIDC config {} ({})", entry.getId(), entry.getName());
        }

        ensureReferencedBySystem(entry.getId());
    }

    private String readSecretFile(Path file, String configId) {
        if (!Files.exists(file)) {
            log.warn("Platform OIDC client-secret file missing for {}: {} — skipping config until populated",
                     configId, file);
            return null;
        }
        try {
            String value = Files.readString(file).trim();
            if (value.isEmpty()) {
                log.warn("Platform OIDC client-secret file empty for {}: {}", configId, file);
                return null;
            }
            return value;
        } catch (IOException e) {
            log.error("Failed to read platform OIDC client-secret file for {}: {}", configId, file, e);
            return null;
        }
    }

    private void ensureReferencedBySystem(String configId) {
        KinoticSystem system = kinoticSystemService.getSystem().join();
        List<String> ids = system.getOidcConfigurationIds() != null
                ? new ArrayList<>(system.getOidcConfigurationIds())
                : new ArrayList<>();
        if (ids.stream().anyMatch(id -> Objects.equals(id, configId))) return;
        ids.add(configId);
        system.setOidcConfigurationIds(ids);
        kinoticSystemService.save(system).join();
        log.info("Linked platform OIDC config {} to KinoticSystem", configId);
    }
}
