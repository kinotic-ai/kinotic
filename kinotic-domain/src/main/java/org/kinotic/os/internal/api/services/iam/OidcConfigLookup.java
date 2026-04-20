package org.kinotic.os.internal.api.services.iam;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.os.api.model.iam.AuthScopeType;
import org.kinotic.os.api.model.iam.OidcConfiguration;
import org.kinotic.os.api.services.ApplicationService;
import org.kinotic.os.api.services.KinoticSystemService;
import org.kinotic.os.api.services.OrganizationService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Dispatches OIDC configuration lookups to the service that owns the scope entity.
 * Each scope service ({@link KinoticSystemService}, {@link OrganizationService},
 * {@link ApplicationService}) exposes a {@code getOidcConfigurations} method that owns
 * the query for its own entity and the associated config batch fetch.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OidcConfigLookup {

    private final KinoticSystemService kinoticSystemService;
    private final OrganizationService organizationService;
    private final ApplicationService applicationService;

    public CompletableFuture<List<OidcConfiguration>> getConfigsForScope(String authScopeType, String authScopeId) {
        AuthScopeType scope;
        try {
            scope = AuthScopeType.valueOf(authScopeType);
        } catch (IllegalArgumentException e) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("Unsupported authScopeType for OIDC lookup: " + authScopeType));
        }

        return switch (scope) {
            case SYSTEM -> kinoticSystemService.getOidcConfigurations();
            case ORGANIZATION -> organizationService.getOidcConfigurations(authScopeId);
            case APPLICATION -> applicationService.getOidcConfigurations(authScopeId);
        };
    }

}
