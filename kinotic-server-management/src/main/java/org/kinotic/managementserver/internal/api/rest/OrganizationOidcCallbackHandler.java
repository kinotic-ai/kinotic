package org.kinotic.managementserver.internal.api.rest;

import io.vertx.core.Future;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.domain.api.model.security.OidcConfiguration;
import org.kinotic.domain.api.rest.SuppliesGatewayRoutes;
import org.kinotic.domain.api.rest.support.AuthEndpointSupport;
import org.kinotic.domain.api.rest.support.FlowConfigResolver;
import org.kinotic.domain.api.rest.support.OidcFlowOrchestrator;
import org.kinotic.domain.api.rest.support.OidcFlowPurpose;
import org.kinotic.domain.api.rest.support.PendingFlow;
import org.kinotic.domain.api.services.security.OidcConfigurationService;
import org.kinotic.managementserver.api.config.ManagementServerProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The two callbacks a provider returns the browser to for an organization's login: one for the platform's org
 * sign-up registrations and one for an organization's own configurations. A provider returns only to a
 * redirect URI registered in advance, so these are the only URLs an organization registers, and a login, a
 * sign-up, an invitation and connecting a sign-in method all return here; the purpose the start recorded on
 * the flow session says which {@link OidcFlowCompleter} finishes the callback.
 */
@Slf4j
@Component
public class OrganizationOidcCallbackHandler implements SuppliesGatewayRoutes {

    private final OidcFlowOrchestrator oidcFlowOrchestrator;
    private final OidcConfigurationService oidcConfigurationService;
    private final AuthEndpointSupport authEndpointSupport;
    private final ManagementServerProperties properties;
    private final Map<OidcFlowPurpose, OidcFlowCompleter> completers;

    public OrganizationOidcCallbackHandler(OidcFlowOrchestrator oidcFlowOrchestrator,
                                           OidcConfigurationService oidcConfigurationService,
                                           AuthEndpointSupport authEndpointSupport,
                                           ManagementServerProperties properties,
                                           List<OidcFlowCompleter> completers) {
        this.oidcFlowOrchestrator = oidcFlowOrchestrator;
        this.oidcConfigurationService = oidcConfigurationService;
        this.authEndpointSupport = authEndpointSupport;
        this.properties = properties;
        this.completers = completers.stream().collect(Collectors.toMap(OidcFlowCompleter::purpose, Function.identity()));
    }

    @Override
    public void mountRoutes(Router router) {
        router.get(OrganizationOidcCallbacks.SOCIAL_CALLBACK_PATH + ":configId").handler(this::handleSocialCallback);
        router.get(OrganizationOidcCallbacks.SSO_CALLBACK_PATH + ":configId").handler(this::handleSsoCallback);
    }

    /**
     * {@code GET /api/auth/org/login/social/callback/:configId} — one of the platform's org sign-up registrations
     * returns here, for a flow of any purpose.
     */
    private void handleSocialCallback(RoutingContext ctx) {
        String configId = ctx.pathParam("configId");
        handle(ctx, configId, OrganizationOidcCallbacks.socialCallbackUrl(properties.getApiBaseUrl(), configId),
               (_, _, _) -> oidcConfigurationService.findOrgSignupById(configId).map(signup -> signup));
    }

    /**
     * {@code GET /api/auth/org/login/sso/callback/:configId} — an organization's own configuration returns here,
     * for a flow of any purpose; the configuration is resolved against the organization the flow stashed, which
     * also confirms the organization's login offers it.
     */
    private void handleSsoCallback(RoutingContext ctx) {
        String configId = ctx.pathParam("configId");
        handle(ctx, configId, OrganizationOidcCallbacks.ssoCallbackUrl(properties.getApiBaseUrl(), configId),
               (orgId, _, _) -> orgId == null
                       ? Future.succeededFuture()
                       : oidcConfigurationService.findOffered(configId, orgId, null));
    }

    private void handle(RoutingContext ctx, String configId, String callbackUrl, FlowConfigResolver<OidcConfiguration> resolver) {
        // read before the callback is handled, which consumes the flow whether or not it validates
        PendingFlow flow = oidcFlowOrchestrator.pendingFlow(ctx);
        oidcFlowOrchestrator.handleCallback(ctx, configId, callbackUrl, resolver)
                            .onSuccess(result -> completers.get(result.purpose()).complete(ctx, result))
                            .onFailure(failure -> {
                                log.warn("OIDC callback failed for config {}: {}", configId, failure.getMessage());
                                if (flow == null || flow.purpose() == null) {
                                    // no flow of this session's is waiting, so there is no page to return to but the login
                                    authEndpointSupport.redirectCallbackFailure(ctx, failure);
                                } else {
                                    completers.get(flow.purpose()).fail(ctx, flow, failure);
                                }
                            });
    }
}
