package org.kinotic.domain.api.rest.support;

import com.github.benmanes.caffeine.cache.AsyncCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.Session;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.common.api.oidc.InvalidIdTokenException;
import org.kinotic.common.api.oidc.OidcAuthorization;
import org.kinotic.common.api.oidc.OidcClient;
import org.kinotic.common.api.oidc.OidcClientService;
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
import org.kinotic.domain.internal.api.rest.support.OidcFlowSession;

import java.time.Duration;
import java.util.regex.Pattern;
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

    private static final String OIDC_FLOW_SESSION_KEY = "oidcFlow";
    private static final Pattern OAUTH_ERROR_CODE = Pattern.compile("^[a-z0-9_]{1,64}$");

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

        String code = ctx.request().getParam("code");
        String state = ctx.request().getParam("state");
        String idpError = ctx.request().getParam("error");

        // consumed first, so a flow the IdP refused or a malformed return cannot be completed by a later callback
        Session session = ctx.session();
        OidcFlowSession flowSession = session.remove(OIDC_FLOW_SESSION_KEY);

        if (idpError != null) {
            // an OAuth error is a short code (RFC 6749 §4.1.2.1); anything else arriving on the public callback is
            // reflected into neither the log nor the browser
            String error = OAUTH_ERROR_CODE.matcher(idpError).matches() ? idpError : OidcErrorCodes.INVALID_CALLBACK;
            log.info("OIDC callback error from config {}: {}", pathConfigId, error);
            return Future.failedFuture(new OidcCallbackException(error));
        }
        if (code == null || state == null) {
            return Future.failedFuture(new OidcCallbackException(OidcErrorCodes.INVALID_CALLBACK));
        }

        if (flowSession == null || !flowSession.authorization().state().equals(state)
                || !flowSession.configId().equals(pathConfigId)) {
            log.warn("OIDC callback state mismatch for configId={}", pathConfigId);
            return Future.failedFuture(new OidcCallbackException(OidcErrorCodes.STATE_MISMATCH));
        }

        return configResolver.resolve(flowSession.orgId(), flowSession.applicationId(), flowSession.tenantId())
                     .compose(config -> {
                         // Re-checked here rather than in each resolver: a config disabled while the
                         // user was at the IdP must not complete the flow it started.
                         if (config == null || !config.isEnabled()) {
                             return Future.failedFuture(new OidcCallbackException(OidcErrorCodes.CONFIG_NOT_FOUND));
                         }
                         return getOidcClient(config)
                                 .compose(client -> client.verifiedClaims(code, callbackUrl, flowSession.authorization()))
                                 .recover(err -> {
                                     Future<Map<String, Object>> failed;
                                     if (err instanceof InvalidIdTokenException) {
                                         log.warn("OIDC id token rejected for config {}: {}", config.getId(), err.getMessage());
                                         failed = Future.failedFuture(new OidcCallbackException(OidcErrorCodes.INVALID_TOKEN));
                                     } else {
                                         failed = Future.failedFuture(err);
                                     }
                                     return failed;
                                 })
                                 .map(claims -> new CallbackResult<>(config, claims, flowSession.purpose(), flowSession.orgId(),
                                                                   flowSession.applicationId(), flowSession.tenantId(),
                                                                   flowSession.inviteToken(), flowSession.organizationUserId(),
                                                                   flowSession.origin()));
                     });
    }

    /**
     * The flow the session holds for the callback about to be handled, or {@code null} when it holds none, for
     * a handler to route a failed callback by; {@link #handleCallback} consumes the flow itself.
     */
    public PendingFlow pendingFlow(RoutingContext ctx) {
        OidcFlowSession flowSession = ctx.session().get(OIDC_FLOW_SESSION_KEY);
        return flowSession == null ? null : new PendingFlow(flowSession.purpose(), flowSession.orgId(), flowSession.applicationId(),
                                                            flowSession.origin(), flowSession.inviteToken());
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

        OidcAuthorization authorization = OidcAuthorization.create();
        return getOidcClient(config)
                .map(client -> client.authorizationUrl(authorization, callbackUrl, scopes))
                // stored once the provider is reachable, so a start that fails leaves the session's pending flow intact
                .onSuccess(url -> {
                    Session session = ctx.session();
                    session.regenerateId();
                    session.put(OIDC_FLOW_SESSION_KEY, new OidcFlowSession(authorization, config.getId(), purpose, orgId, applicationId,
                                                                           tenantId, inviteToken, organizationUserId, origin));
                });
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
