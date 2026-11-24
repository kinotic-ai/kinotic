package org.kinotic.domain.api.rest.support;

import org.kinotic.common.api.oidc.OidcCallbackException;
import org.kinotic.common.api.oidc.OidcErrorCodes;
import com.github.benmanes.caffeine.cache.AsyncCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.ext.web.RoutingContext;
import io.vertx.core.json.JsonObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.common.api.oidc.OidcClient;
import org.kinotic.common.api.oidc.OidcClientService;
import org.kinotic.common.api.oidc.OidcFlowState;
import org.kinotic.common.api.oidc.OidcSignInFlow;
import org.kinotic.core.api.utils.KinoticUtil;
import org.kinotic.domain.api.services.SecretReferenceResolver;
import org.apache.commons.lang3.Validate;
import org.kinotic.domain.api.config.KinoticDomainProperties;
import org.kinotic.core.api.security.SessionBinding;
import org.kinotic.domain.api.model.security.OidcConfiguration;
import org.kinotic.domain.api.model.security.OwnedOidcConfiguration;
import org.kinotic.domain.api.model.ApplicationKey;
import org.kinotic.domain.api.model.security.ManagedOidcConfiguration;
import org.kinotic.domain.api.services.security.OidcConfigurationService;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Owns the OAuth 2.0 / OIDC flow shared by every handler that bounces the user out to
 * an IdP and back: state/nonce/PKCE generation, session storage, callback validation,
 * code exchange, claim extraction, issuer validation. Handlers compose it with their
 * own per-route config resolver — the orchestrator itself knows nothing about ParticipantIdentity
 * provisioning, JWT minting, or which entity table the configuration came from.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OidcFlowOrchestrator {

    private static final String PURPOSE = "purpose";
    private static final String ORG_ID = "orgId";
    private static final String APPLICATION_ID = "applicationId";
    private static final String TENANT_ID = "tenantId";
    private static final String INVITE_TOKEN = "inviteToken";
    private static final String ORGANIZATION_USER_ID = "organizationUserId";

    private final OidcSignInFlow signInFlow = new OidcSignInFlow("oidcFlow");

    private final AsyncCache<String, OidcClient> oidcClientCache =
            Caffeine.newBuilder()
                    // a discovered client holds a periodic JWKS refresh timer; close() cancels it, so an entry
                    // dropped by either policy does not leak one
                    .removalListener((String id, OidcClient client, RemovalCause cause) -> client.close())
                    .expireAfterAccess(Duration.ofHours(1))
                    .maximumSize(1_000)
                    .buildAsync();
    private final SecretReferenceResolver secretReferenceResolver;
    private final OidcConfigurationService oidcConfigurationService;
    private final KinoticDomainProperties domainProperties;
    private final Vertx vertx;
    private final OidcClientService oidcClientService;

    /**
     * Validates the callback (state match, no IdP error), exchanges the code, and returns the
     * configuration along with the verified identity claims ({@code sub}, {@code email},
     * {@code email_verified}, {@code name}, …).
     * The handler decides what to do with the claims (look up a ParticipantIdentity, create a
     * {@code PendingSignUp}, etc.).
     *
     * <p>The session is consumed regardless of outcome — replay protection.
     *
     * @param configResolver loads the config for this route, scoped by the organization, application and
     *                       tenant recovered from the flow session (each {@code null} when the flow stashed
     *                       none — routes scoped otherwise ignore them). Must resolve to {@code null} when
     *                       the config is unknown; a config that is no longer enabled is rejected here, so
     *                       the resolver need not check it.
     */
    public <C extends OidcConfiguration> Future<CallbackResult<C>> handleCallback(
            RoutingContext ctx,
            String pathConfigId,
            String callbackUrl,
            FlowConfigResolver<C> configResolver) {

        Future<CallbackResult<C>> ret;
        try {
            OidcFlowState flow = signInFlow.consume(ctx);
            JsonObject scope = flow.context();
            if (!flow.registrationId().equals(pathConfigId)) {
                log.warn("OIDC callback state mismatch for configId={}", KinoticUtil.singleLine(pathConfigId));
                ret = Future.failedFuture(new OidcCallbackException(OidcErrorCodes.STATE_MISMATCH));
            } else {
                ret = configResolver.resolve(scope.getString(ORG_ID), scope.getString(APPLICATION_ID), scope.getString(TENANT_ID))
                                    .compose(config -> {
                                        // Re-checked here rather than in each resolver: a config disabled while the
                                        // user was at the IdP must not complete the flow it started.
                                        if (config == null || !config.isEnabled()) {
                                            return Future.failedFuture(new OidcCallbackException(OidcErrorCodes.CONFIG_NOT_FOUND));
                                        }
                                        return getOidcClient(config)
                                                .compose(client -> signInFlow.verifiedClaims(ctx, client, callbackUrl, flow))
                                                .map(claims -> new CallbackResult<>(config, claims, purposeOf(flow), scope.getString(ORG_ID),
                                                                                  scope.getString(APPLICATION_ID), scope.getString(TENANT_ID),
                                                                                  scope.getString(INVITE_TOKEN),
                                                                                  scope.getString(ORGANIZATION_USER_ID), flow.origin()));
                                    });
            }
        } catch (OidcCallbackException refused) {
            log.warn("OIDC callback for config {} refused: {}", KinoticUtil.singleLine(pathConfigId), refused.getErrorCode());
            ret = Future.failedFuture(refused);
        }
        return ret;
    }

    private static OidcFlowPurpose purposeOf(OidcFlowState flow) {
        return OidcFlowPurpose.valueOf(flow.context().getString(PURPOSE));
    }

    /**
     * The flow the session holds for the callback about to be handled, or {@code null} when it holds none, for
     * a handler to route a failed callback by; {@link #handleCallback} consumes the flow itself.
     */
    public PendingFlow pendingFlow(RoutingContext ctx) {
        OidcFlowState flow = signInFlow.pending(ctx);
        return flow == null ? null : new PendingFlow(purposeOf(flow), flow.context().getString(ORG_ID),
                                                     flow.context().getString(APPLICATION_ID), flow.origin(),
                                                     flow.context().getString(INVITE_TOKEN));
    }

    /**
     * Begins a {@link OidcFlowPurpose#LOGIN} flow signing in to the organization {@code orgId}, or to none when
     * it is {@code null}: generates state/nonce/PKCE, stores them on the session along with the page at
     * {@code origin} the completed login is bound to and the browser returns to, builds the IdP authorization URL
     * using the supplied callback URL, and returns the URL. The caller names the origin because a start reached by
     * a link carries no {@code Origin} of its own. An origin that is neither the portal's nor one
     * {@code kinotic.cors.allowedOriginPattern} trusts is stored as none, so the flow returns to this server's UI.
     */
    public Future<String> startFlow(RoutingContext ctx,
                                    OidcConfiguration config,
                                    String callbackUrl,
                                    String orgId,
                                    String origin) {
        return startFlow(ctx, config, callbackUrl, OidcFlowPurpose.LOGIN, orgId, null, null, null, null, trustedOrigin(origin));
    }

    /**
     * Begins a {@link OidcFlowPurpose#LOGIN} flow signing in to {@code application}, or to its tenant
     * {@code tenantId} when one is named, as {@link #startFlow(RoutingContext, OidcConfiguration, String, String, String)}
     * does, stashing the ids of the scope for the callback to resolve its configuration by. {@code origin} must be
     * one of the application's UIs, which the caller checks.
     */
    public Future<String> startFlow(RoutingContext ctx,
                                    OidcConfiguration config,
                                    String callbackUrl,
                                    ApplicationKey application,
                                    String tenantId,
                                    String origin) {
        return startFlow(ctx, config, callbackUrl, OidcFlowPurpose.LOGIN, application.organizationId(), application.applicationId(),
                         tenantId, null, null, origin);
    }

    /**
     * Begins a {@link OidcFlowPurpose#SIGN_UP} flow through one of the system's org sign-up registrations, which
     * returns to the page that sent the request when it is the portal or {@code kinotic.cors.allowedOriginPattern}
     * trusts it, and to this server's UI otherwise.
     */
    public Future<String> startSignUp(RoutingContext ctx, OidcConfiguration config, String callbackUrl) {
        return startFlow(ctx, config, callbackUrl, OidcFlowPurpose.SIGN_UP, null, null, null, null, null, trustedOrigin(SessionBinding.origin(ctx)));
    }

    /**
     * Begins an {@link OidcFlowPurpose#INVITATION} flow accepting the invitation {@code inviteToken} into
     * organization {@code orgId}, stashed for the callback to resolve its configuration by and to complete the
     * acceptance with. It returns to the page that sent the request when it is the portal or
     * {@code kinotic.cors.allowedOriginPattern} trusts it, and to this server's UI otherwise.
     */
    public Future<String> startInvitation(RoutingContext ctx, OidcConfiguration config, String callbackUrl, String orgId, String inviteToken) {
        Validate.notBlank(inviteToken, "inviteToken cannot be blank");
        return startFlow(ctx, config, callbackUrl, OidcFlowPurpose.INVITATION, orgId, null, null, inviteToken, null, trustedOrigin(SessionBinding.origin(ctx)));
    }

    /**
     * Begins a {@link OidcFlowPurpose#CONNECT} flow binding the identity the provider asserts to organization user
     * {@code organizationUserId}; {@code orgId} is the organization whose configuration the flow runs through, or
     * {@code null} for one of the system's registrations. It returns to the page that sent the request when it is the
     * portal or {@code kinotic.cors.allowedOriginPattern} trusts it, and to this server's UI otherwise.
     */
    public Future<String> startConnect(RoutingContext ctx, OidcConfiguration config, String callbackUrl, String orgId, String organizationUserId) {
        Validate.notBlank(organizationUserId, "organizationUserId cannot be blank");
        return startFlow(ctx, config, callbackUrl, OidcFlowPurpose.CONNECT, orgId, null, null, null, organizationUserId, trustedOrigin(SessionBinding.origin(ctx)));
    }

    // a flow sends its outcome (a login, a sign-up token, an error saying whether an account exists) to the page it
    // returns to, so only the portal or a page the CORS pattern names gets it; the portal is named separately because
    // a portal served from the API's own origin needs no CORS entry
    private String trustedOrigin(String origin) {
        boolean trusted = domainProperties.getCors().trustsOrigin(origin)
                          || (origin != null && origin.equals(domainProperties.getDomain().portalOrigin()));
        return trusted ? origin : null;
    }

    private Future<String> startFlow(RoutingContext ctx,
                                     OidcConfiguration config,
                                     String callbackUrl,
                                     OidcFlowPurpose purpose,
                                     String orgId,
                                     String applicationId,
                                     String tenantId,
                                     String inviteToken,
                                     String organizationUserId,
                                     String origin) {
        // Scopes ride as RFC 6749's space-delimited string; a managed registration may name its own, every
        // other configuration gets the standard OIDC triple.
        List<String> scopes = config instanceof ManagedOidcConfiguration social
                              && social.getScopes() != null && !social.getScopes().isBlank()
                ? List.of(social.getScopes().split(" "))
                : List.of("openid", "email", "profile");

        OidcFlowState flow = OidcFlowState.create(config.getId(), origin, new JsonObject()
                .put(PURPOSE, purpose.name())
                .put(ORG_ID, orgId)
                .put(APPLICATION_ID, applicationId)
                .put(TENANT_ID, tenantId)
                .put(INVITE_TOKEN, inviteToken)
                .put(ORGANIZATION_USER_ID, organizationUserId));
        // the flow is kept once the provider is reachable, so a start that fails leaves the session's pending flow intact
        return getOidcClient(config).map(client -> signInFlow.start(ctx, client, callbackUrl, scopes, flow));
    }

    /**
     * The {@link OidcClient} for {@code config} as it is currently persisted, discovered against its
     * authority on first use and cached until an hour passes with no flow using it. Saving the
     * configuration takes effect on the next flow. A discovery that fails is not cached, so the next
     * flow retries it.
     */
    private Future<OidcClient> getOidcClient(OidcConfiguration config) {
        // Keying on updated as well as id is what picks up an edited clientId/authority or a replaced
        // client secret, which is saved along with the row: both call sites pass a row read for this
        // request, so a save misses the cache on every node without an invalidation message, and the
        // superseded entry ages out on its own.
        String key = config.getId() + ':' + (config.getUpdated() != null ? config.getUpdated().getTime() : 0);
        // AsyncCache evicts an entry whose future completes exceptionally, which is what keeps a
        // transient discovery failure from being cached — the loader must not touch the cache itself
        CompletableFuture<OidcClient> cached =
                oidcClientCache.get(key,
                                    (id, executor) -> clientSecretOf(config)
                                            .compose(secret -> oidcClientService.createClient(config, secret))
                                            .toCompletionStage()
                                            .toCompletableFuture());
        return Future.fromCompletionStage(cached, vertx.getOrCreateContext())
                     .onFailure(err -> log.error("Failed to initialize the OIDC client for config {}", config.getId(), err));
    }

    /**
     * The client secret {@code config} sends its provider, or null for a public client, read from where
     * its kind of configuration keeps it: the system's secret storage, under the name a managed
     * registration's {@code secretNameRef} gives, or the owning organization's scope of the secret storage for a
     * configuration an organization saved.
     */
    private Future<String> clientSecretOf(OidcConfiguration config) {
        return switch (config) {
            case ManagedOidcConfiguration managed -> secretReferenceResolver.resolve(managed.getSecretNameRef());
            // an organization's administrator controls this row, so nothing on it may name a system secret
            case OwnedOidcConfiguration owned -> oidcConfigurationService.findClientSecret(owned);
        };
    }

}
