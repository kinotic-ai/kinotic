package org.kinotic.managementserver.internal.api.rest;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.HttpException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.security.SessionBinding;
import org.kinotic.domain.api.config.KinoticDomainProperties;
import org.kinotic.management.api.config.KinoticManagementApiProperties;
import org.kinotic.domain.api.exceptions.UserDisabledException;
import org.kinotic.domain.api.model.security.OidcConfiguration;
import org.kinotic.domain.api.model.security.OrgSignupOidcConfiguration;
import org.kinotic.domain.api.model.security.OwnedOidcConfiguration;
import org.kinotic.domain.api.model.security.LoginProviderType;
import org.kinotic.domain.api.model.security.PasswordConfiguration;
import org.kinotic.domain.api.rest.support.OAuth2Util;
import org.kinotic.domain.api.rest.support.OidcCallbackException;
import org.kinotic.domain.api.rest.support.OidcCallbacks;
import org.kinotic.core.api.security.Participant;
import org.kinotic.domain.api.model.security.identity.OrganizationParticipantIdentity;
import org.kinotic.core.api.service.SuppliesGatewayRoutes;
import org.kinotic.domain.api.rest.support.AuthEndpointSupport;
import org.kinotic.domain.api.rest.support.CallbackResult;
import org.kinotic.domain.api.rest.support.OidcErrorCodes;
import org.kinotic.domain.api.rest.support.OidcFlowOrchestrator;
import org.kinotic.domain.api.rest.support.OidcFlowPurpose;
import org.kinotic.domain.api.rest.support.PendingFlow;
import org.kinotic.domain.api.services.OrganizationService;
import org.kinotic.domain.api.services.security.InviteService;
import org.kinotic.domain.api.services.security.LocalAuthenticationService;
import org.kinotic.domain.api.services.security.OidcConfigurationService;
import org.kinotic.domain.api.services.security.PasswordConfigurationService;
import org.kinotic.domain.api.services.security.ParticipantIdentityService;
import org.kinotic.managementserver.api.config.ManagementServerProperties;
import org.kinotic.managementserver.api.config.TurnstileProperties;
import org.kinotic.managementserver.internal.api.services.TurnstileVerifier;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Login routes for an organization's members. Every login is addressed to the organization by its id, the
 * slug of the portal's {@code /login/<orgId>} page, and signs in one of its users: a person in several
 * organizations is a separate user of each and signs in to each at its own login. The page offers the methods
 * that organization's login supports, as {@code GET /api/auth/org/login/:orgId/providers} lists them, without
 * asking about an email first, so no route tells whether an email has a user. The portal's landing page, which
 * knows no organization yet, asks for one and offers the system's social registrations, which every organization's
 * login offers: a sign-in there signs in the one user the identity is bound to, or offers the organizations of the
 * users it is bound to. It also emails a person who does not remember their organizations the list. On success
 * each login establishes the browser session; the STOMP WebSocket handshake then authenticates
 * from that session cookie, so the browser never handles a token. OIDC flows started here return to
 * {@link OrganizationOidcCallbackHandler}, which hands a login's callback back here to complete.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrganizationLoginHandler implements SuppliesGatewayRoutes, OidcFlowCompleter {

    private final AuthEndpointSupport authEndpointSupport;
    private final ManagementServerProperties properties;
    private final KinoticDomainProperties domainProperties;
    private final KinoticManagementApiProperties managementApiProperties;
    private final ParticipantIdentityService identityService;
    private final InviteService inviteService;
    private final OrganizationService organizationService;
    private final LocalAuthenticationService localAuthenticationService;
    private final OidcConfigurationService oidcConfigurationService;
    private final PasswordConfigurationService passwordConfigurationService;
    private final OidcFlowOrchestrator oidcFlowOrchestrator;
    private final TurnstileVerifier turnstileVerifier;

    private static final String ORGANIZATION_CHOICES = "organizationChoices";
    // long enough for the page the sign-in returns to to load and ask, short enough that the ids are gone
    // from a browser left on the landing page
    private static final Duration ORGANIZATION_CHOICES_VALIDITY = Duration.ofMinutes(2);

    @Override
    public void mountRoutes(Router router) {
        // addressed to no organization
        router.get("/api/auth/org/login/providers").handler(this::handleProviders);
        router.get("/api/auth/org/login/choices").handler(this::handleChoices);
        router.get("/api/auth/org/login/find").handler(this::handleFindChallenge);
        router.post("/api/auth/org/login/find").handler(this::handleFind);
        router.post("/api/auth/org/login/social/start/:provider").handler(this::handleSocialStart);
        // one organization's login
        router.get("/api/auth/org/login/:orgId/providers").handler(this::handleOrganizationProviders);
        router.post("/api/auth/org/login/:orgId").handler(this::handleLogin);
        router.post("/api/auth/org/login/:orgId/start/:configId").handler(this::handleStart);
        // a link, as from a provider's own tile of applications, starts the same flow
        router.get("/api/auth/org/login/:orgId/start/:configId").handler(this::handleStart);
    }

    /**
     * {@code GET /api/auth/org/login/choices} — the ids of the organizations the last landing-page sign-in the
     * requesting page started found the identity bound to a user of, {@code ["acme", "globex"]}, for the page to
     * offer after {@link OidcErrorCodes#MULTIPLE_ORGANIZATIONS}. The ids are answered for two minutes after that
     * sign-in and until the page starts a login at an organization; {@code []} otherwise.
     */
    private void handleChoices(RoutingContext ctx) {
        JsonArray choices = new JsonArray();
        // only a request presenting the cookie consults the session, since reading a lazy session creates one
        if (SessionBinding.sessionPresented(ctx)) {
            JsonObject offer = ctx.session().get(choicesKey(SessionBinding.origin(ctx)));
            if (offer != null && System.currentTimeMillis() - offer.getLong("at") < ORGANIZATION_CHOICES_VALIDITY.toMillis()) {
                choices = offer.getJsonArray("organizations");
            }
        }
        ctx.response()
           .putHeader("Content-Type", "application/json")
           .end(choices.encode());
    }

    private static String choicesKey(String origin) {
        return SessionBinding.pageKey(ORGANIZATION_CHOICES, origin);
    }

    // the page chose an organization and is signing in there, so the offer it was made is over
    private static void endOffer(RoutingContext ctx) {
        if (SessionBinding.sessionPresented(ctx)) {
            ctx.session().remove(choicesKey(SessionBinding.origin(ctx)));
        }
    }

    /**
     * {@code GET /api/auth/org/login/providers} — the unique provider keys (e.g. {@code "github"}) of the
     * system's org sign-up registrations, for the landing page's and the sign-up page's social-button rows and
     * the members page: one key per provider kind, even when several configs share a kind.
     */
    private void handleProviders(RoutingContext ctx) {
        oidcConfigurationService.findEnabledOrgSignupProviders()
              .onSuccess(configs -> {
                  JsonArray providers = new JsonArray();
                  Set<String> seen = new LinkedHashSet<>();
                  for (OrgSignupOidcConfiguration c : configs) {
                      if (c.getProvider() == null) continue;
                      String key = c.getProvider().key();
                      if (seen.add(key)) providers.add(key);
                  }
                  ctx.response().putHeader("Content-Type", "application/json").end(providers.encode());
              })
              .onFailure(err -> {
                  log.warn("Failed to list system providers: {}", err.getMessage());
                  authEndpointSupport.respondError(ctx, 500, "Failed to list providers");
              });
    }

    /**
     * {@code GET /api/auth/org/login/find} — what the page must present to have an address's organizations
     * emailed: {@code turnstileSiteKey}, the Cloudflare Turnstile site whose token the {@code POST} requires, absent
     * when the deployment requires none, and {@code showLinksInUI}, {@code true} when the {@code POST} answers with
     * the organizations in place of an email.
     */
    private void handleFindChallenge(RoutingContext ctx) {
        TurnstileProperties turnstile = properties.getTurnstile();
        JsonObject challenge = new JsonObject().put("showLinksInUI", !domainProperties.getDomain().getEmail().isEnabled());
        if (turnstile.isEnabled()) {
            challenge.put("turnstileSiteKey", turnstile.getSiteKey());
        }
        ctx.response().putHeader("Content-Type", "application/json").end(challenge.encode());
    }

    /**
     * {@code POST /api/auth/org/login/find {email, turnstileToken}} — emails the address the organizations it may
     * sign in to, each with a link to its login. Always {@code 204} once the token verifies, whether or not the
     * address belongs to any organization, so the response reveals nothing about it; {@code 400} for a token that
     * is missing or refused, since the route is open to anyone and would otherwise send mail on demand. Where email is
     * disabled, answers {@code 200} with the organizations instead,
     * {@code {organizations: [{name, loginUrl}]}}, which reveals them to anyone who asks.
     */
    private void handleFind(RoutingContext ctx) {
        JsonObject body = authEndpointSupport.readJsonBody(ctx);
        String email = body.getString("email");
        if (email == null || email.isBlank()) {
            authEndpointSupport.respondError(ctx, 400, "email is required");
            return;
        }
        turnstileVerifier.verify(body.getString("turnstileToken"))
                         .compose(human -> human
                                 ? organizationService.sendOrganizationsEmail(email)
                                 : Future.failedFuture(new HttpException(400, "Complete the verification and try again")))
                         .onSuccess(links -> {
                             if (links == null) {
                                 ctx.response().setStatusCode(204).end();
                             } else {
                                 JsonArray organizations = new JsonArray();
                                 links.forEach(link -> organizations.add(new JsonObject().put("name", link.name())
                                                                                         .put("loginUrl", link.loginUrl())));
                                 ctx.response()
                                    .putHeader("Content-Type", "application/json")
                                    .end(new JsonObject().put("organizations", organizations).encode());
                             }
                         })
                         .onFailure(err -> {
                             if (err instanceof HttpException http && http.getStatusCode() == 400) {
                                 authEndpointSupport.respondError(ctx, 400, http.getPayload());
                             } else {
                                 log.warn("Could not email the organizations of {}: {}", email, err.getMessage());
                                 authEndpointSupport.respondError(ctx, 500, "Could not send the email");
                             }
                         });
    }

    /**
     * {@code GET /api/auth/org/login/:orgId/providers} — the sign-in providers the organization's login offers,
     * {@code [{id, type, name, provider}]}: its email and password ({@code PASSWORD}) when it takes one, then the
     * system's org sign-up registrations and the organization's own configurations ({@code OIDC}). {@code 404} for an
     * organization that does not exist.
     */
    private void handleOrganizationProviders(RoutingContext ctx) {
        String orgId = ctx.pathParam("orgId");
        organizationService.findById(orgId)
                           .compose(organization -> {
                               Future<JsonArray> ret;
                               if (organization == null) {
                                   ret = Future.failedFuture(new HttpException(404));
                               } else {
                                   Future<PasswordConfiguration> password = passwordConfigurationService.findOffered(orgId, null, null);
                                   Future<List<OidcConfiguration>> providers = oidcConfigurationService.findEnabledForScope(orgId, null);
                                   ret = Future.all(password, providers).map(v -> {
                                       JsonArray offered = authEndpointSupport.providersJson(providers.result());
                                       if (password.result() != null) {
                                           offered.add(0, new JsonObject().put("id", password.result().getId())
                                                                          .put("type", LoginProviderType.PASSWORD.name())
                                                                          .put("name", PasswordConfiguration.NAME)
                                                                          .putNull("provider"));
                                       }
                                       return offered;
                                   });
                               }
                               return ret;
                           })
                           .onSuccess(offered -> ctx.response().putHeader("Content-Type", "application/json").end(offered.encode()))
                           .onFailure(err -> {
                               if (err instanceof HttpException http && http.getStatusCode() == 404) {
                                   authEndpointSupport.respondError(ctx, 404, "Unknown organization: " + orgId);
                               } else {
                                   log.warn("Failed to list the providers of organization {}: {}", orgId, err.getMessage());
                                   authEndpointSupport.respondError(ctx, 500, "Failed to list providers");
                               }
                           });
    }

    /**
     * {@code POST /api/auth/org/login/:orgId {email, password}} — verifies the password of the organization's
     * member with that email, at an organization that allows password sign-in, and establishes the browser
     * session. Generic {@code 401} on any failure.
     */
    private void handleLogin(RoutingContext ctx) {
        String orgId = ctx.pathParam("orgId");
        endOffer(ctx);
        authEndpointSupport.handlePasswordLogin(ctx,
                (email, password) -> localAuthenticationService.authenticateOrganizationUser(email, password, orgId)
                                                               .compose(user -> user == null
                                                                       ? Future.succeededFuture()
                                                                       : identityService.resolveParticipant(user, orgId, null)));
    }

    /**
     * {@code POST|GET /api/auth/org/login/:orgId/start/:configId} — begins a sign-in to the organization
     * through a configuration its login offers, redirecting the browser to the provider, which returns to the
     * callback the configuration is registered with, carrying the organization. {@code 400} for a
     * configuration the login does not offer. The completed login is bound to the page that started it,
     * or to the portal for a start reached by a link, which names no page.
     */
    private void handleStart(RoutingContext ctx) {
        String orgId = ctx.pathParam("orgId");
        String configId = ctx.pathParam("configId");
        String origin = SessionBinding.origin(ctx) != null ? SessionBinding.origin(ctx) : domainProperties.getDomain().portalOrigin();
        endOffer(ctx);

        oidcConfigurationService.findOffered(configId, orgId, null)
              .onSuccess(config -> {
                  if (config == null) {
                      authEndpointSupport.respondError(ctx, 400, "The organization's login does not offer " + configId);
                  } else {
                      authEndpointSupport.redirectToIdp(ctx, config, OidcCallbacks.organizationCallbackUrl(managementApiProperties.getManagementApi().getApiBaseUrl(), config), orgId, origin);
                  }
              })
              .onFailure(err -> {
                  log.warn("Login start failed for {}/{}: {}", orgId, configId, err.getMessage());
                  authEndpointSupport.respondError(ctx, 500, "Failed to start sign-in");
              });
    }

    /**
     * {@code POST /api/auth/org/login/social/start/:provider} — begins a social (OIDC) login from the landing
     * page by redirecting the browser to the chosen Kinotic-curated provider. The flow names no
     * organization, so its callback finds the organizations whose users the identity is bound to.
     */
    private void handleSocialStart(RoutingContext ctx) {
        authEndpointSupport.handleSocialStart(ctx, config -> oidcFlowOrchestrator.startFlow(
                ctx, config, OidcCallbacks.socialCallbackUrl(managementApiProperties.getManagementApi().getApiBaseUrl(), config.getId()), null, SessionBinding.origin(ctx)));
    }

    @Override
    public OidcFlowPurpose purpose() {
        return OidcFlowPurpose.LOGIN;
    }

    /**
     * Signs the identity the callback verified in: to the organization the flow names, or, for a flow the landing
     * page started through one of the system's registrations, to the one organization whose user the identity is
     * bound to. An identity bound to users of several is sent back with {@link OidcErrorCodes#MULTIPLE_ORGANIZATIONS}
     * and those organizations, to choose one and sign in at its own login.
     */
    @Override
    public void complete(RoutingContext ctx, CallbackResult<OidcConfiguration> result) {
        if (result.orgId() == null) {
            completeLandingLogin(ctx, result);
        } else {
            completeOrganizationLogin(ctx, result);
        }
    }

    @Override
    public void fail(RoutingContext ctx, PendingFlow flow, Throwable failure) {
        authEndpointSupport.redirectCallbackFailure(ctx, flow.origin(), failure);
    }

    private void completeLandingLogin(RoutingContext ctx, CallbackResult<OidcConfiguration> result) {
        String sub = authEndpointSupport.verifiedSubject(ctx, result);
        if (sub != null) {
            identityService.findOrganizationUsersByOidcIdentity(result.config().getId(), sub)
                           .map(OrganizationLoginHandler::enabledUsers)
                           .onSuccess(users -> signInOrOffer(ctx, result, users))
                           .onFailure(err -> authEndpointSupport.completeLogin(ctx, result.origin(), result.config().getProvider(), Future.failedFuture(err)));
        }
    }

    // the users that may sign in; an identity whose every user is disabled is told so, as the organization's own
    // login would tell it
    private static List<OrganizationParticipantIdentity> enabledUsers(List<OrganizationParticipantIdentity> users) {
        List<OrganizationParticipantIdentity> enabled = users.stream().filter(OrganizationParticipantIdentity::isEnabled).toList();
        if (enabled.isEmpty() && !users.isEmpty()) {
            throw new OidcCallbackException(OidcErrorCodes.USER_DISABLED);
        }
        return enabled;
    }

    // an identity bound to users of several organizations has proven who it is, so the landing page may offer those
    // organizations for the person to choose from, where a stranger learns nothing; the page that started the
    // sign-in reads them from handleChoices, so the redirect itself carries no ids
    private void signInOrOffer(RoutingContext ctx, CallbackResult<OidcConfiguration> result, List<OrganizationParticipantIdentity> users) {
        String origin = result.origin();
        if (users.size() > 1) {
            // read without being removed, since a concurrent request on the session would make the removal
            // unreliable: the offer expires by its timestamp and ends when the page starts an organization's login
            JsonObject offer = new JsonObject()
                    .put("organizations", new JsonArray(users.stream().map(OrganizationParticipantIdentity::getOrganizationId).sorted().toList()))
                    .put("at", System.currentTimeMillis());
            ctx.session().put(choicesKey(origin), offer);
            authEndpointSupport.redirectToContinue(ctx, origin, OidcErrorCodes.MULTIPLE_ORGANIZATIONS);
        } else {
            authEndpointSupport.completeLogin(ctx, origin, result.config().getProvider(), users.isEmpty() ? Future.succeededFuture() : signIn(users.getFirst()));
        }
    }

    /**
     * Logs the organization's user bound to the identity the callback verified into the organization the flow names.
     * An identity bound to none of its users signs in, through one of the organization's own configurations, the
     * member the organization added under the identity's verified email that nobody has signed in as yet, and is
     * bound to that user as its first sign-in method; through a system registration, or for an email that is no such
     * member's, there is no one to sign in.
     */
    private void completeOrganizationLogin(RoutingContext ctx, CallbackResult<OidcConfiguration> result) {
        String sub = authEndpointSupport.verifiedSubject(ctx, result);
        if (sub != null) {
            String email = OAuth2Util.stringClaim(result.claims(), "email");
            Future<Participant> participant = identityService.findOrganizationUserByOidcIdentity(result.config().getId(), sub, result.orgId())
                    .compose(user -> {
                        Future<Participant> ret;
                        if (user != null) {
                            ret = signIn(user);
                        } else if (result.config() instanceof OwnedOidcConfiguration && email != null) {
                            // only the organization's own directory may claim the member the organization provisioned
                            ret = identityService.claimProvisionedOrganizationUser(result.config().getId(), sub, email, result.orgId())
                                                 .recover(err -> Future.failedFuture(err instanceof UserDisabledException
                                                         ? new OidcCallbackException(OidcErrorCodes.USER_DISABLED) : err))
                                                 // the claim spends the invitation the member was sent
                                                 .compose(claimed -> claimed != null
                                                         ? inviteService.deletePendingInvite(email, result.orgId(), null).map(claimed)
                                                         : Future.succeededFuture());
                        } else {
                            ret = Future.succeededFuture();
                        }
                        return ret;
                    });
            authEndpointSupport.completeLogin(ctx, result.origin(), result.config().getProvider(), participant);
        }
    }

    // a disabled user fails the sign-in with the error code the login page names
    private Future<Participant> signIn(OrganizationParticipantIdentity user) {
        return user.isEnabled()
                ? identityService.resolveParticipant(user, user.getOrganizationId(), null)
                : Future.failedFuture(new OidcCallbackException(OidcErrorCodes.USER_DISABLED));
    }
}
