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
import org.kinotic.domain.api.model.security.identity.OrganizationMembership;
import org.kinotic.domain.api.rest.support.OAuth2Util;
import org.kinotic.domain.api.rest.support.OidcCallbackException;
import org.kinotic.domain.api.rest.support.OidcCallbacks;
import org.kinotic.domain.api.services.security.OrganizationMembershipService;
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
import org.kinotic.domain.api.services.security.LocalAuthenticationService;
import org.kinotic.domain.api.services.security.OidcConfigurationService;
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
 * Login routes for an organization's members. A login is addressed to the organization by its id, the
 * slug of the portal's {@code /login/<orgId>} page, since an email or a social identity may be a member
 * of any number of organizations: the page offers the methods that organization's login supports,
 * email and password and the configurations {@code GET /api/auth/org/login/:orgId/providers} lists,
 * without asking about an email first, so no route tells whether an email has a user. The portal's
 * landing page, which knows no organization yet, offers the system's social registrations: a
 * sign-in there finds the one organization the identity is a member of, and emails a person who does
 * not remember their organizations the list. On success each login establishes the browser session;
 * the STOMP WebSocket handshake then authenticates from that session cookie, so the browser never
 * handles a token. OIDC flows started here return to {@link OrganizationOidcCallbackHandler}, which hands
 * a login's callback back here to complete.
 *
 * <p>A member is an organization user with a membership of the organization: the login finds the organization
 * user by its email or by the identity its provider asserts, then signs it in through that membership.
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
    private final OrganizationMembershipService membershipService;
    private final OrganizationService organizationService;
    private final LocalAuthenticationService localAuthenticationService;
    private final OidcConfigurationService oidcConfigurationService;
    private final OidcFlowOrchestrator oidcFlowOrchestrator;
    private final TurnstileVerifier turnstileVerifier;

    private static final String ORGANIZATION_CHOICES = "organizationChoices";
    // long enough for the page the sign-in returns to to load and ask, short enough that the ids are gone
    // from a browser left on the landing page
    private static final Duration ORGANIZATION_CHOICES_VALIDITY = Duration.ofMinutes(2);

    @Override
    public void mountRoutes(Router router) {
        // the landing page, addressed to no organization
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
        // a signed-in organization user moves between the organizations it is a member of
        router.post("/api/auth/org/switch").handler(this::handleSwitch);
    }

    /**
     * {@code POST /api/auth/org/switch {organizationId}} — signs the page's signed-in organization user in to
     * another organization it is a member of, replacing the page's login; {@code 204} once switched, {@code 403} for an
     * organization the user may not sign in to, {@code 400} without one, {@code 401} without a signed-in user.
     */
    private void handleSwitch(RoutingContext ctx) {
        Participant current = authEndpointSupport.requireSessionUser(ctx);
        String organizationId = authEndpointSupport.readJsonBody(ctx).getString("organizationId");
        if (organizationId == null || organizationId.isBlank()) {
            authEndpointSupport.respondError(ctx, 400, "organizationId is required");
            return;
        }
        identityService.resolveParticipant(current.getId(), organizationId, null)
                       .onSuccess(participant -> {
                           if (participant == null) {
                               authEndpointSupport.respondError(ctx, 403, "You are not a member of that organization");
                           } else {
                               authEndpointSupport.respondSuccess(ctx, participant);
                           }
                       })
                       .onFailure(err -> {
                           log.warn("Switching {} to organization {} failed: {}", current.getId(), organizationId, err.getMessage());
                           authEndpointSupport.respondError(ctx, 500, "Failed to switch organization");
                       });
    }

    /**
     * {@code GET /api/auth/org/login/choices} — the ids of the organizations the last landing-page sign-in the
     * requesting page started found the identity a member of, {@code ["acme", "globex"]}, for the page to offer
     * after {@link OidcErrorCodes#MULTIPLE_ORGANIZATIONS}. The ids are answered for two minutes after that
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
     * system's org sign-up registrations, for the landing page's social-button row and the invite
     * dialog: one key per provider kind, even when several configs share a kind.
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
     * emailed: {@code {turnstileSiteKey}}, the Cloudflare Turnstile site whose token the {@code POST} requires, or
     * {@code {}} when the deployment requires none.
     */
    private void handleFindChallenge(RoutingContext ctx) {
        TurnstileProperties turnstile = properties.getTurnstile();
        JsonObject challenge = new JsonObject();
        if (turnstile.isEnabled()) {
            challenge.put("turnstileSiteKey", turnstile.getSiteKey());
        }
        ctx.response().putHeader("Content-Type", "application/json").end(challenge.encode());
    }

    /**
     * {@code POST /api/auth/org/login/find {email, turnstileToken}} — emails the address the organizations it may
     * sign in to, each with a link to its login. Always {@code 204} once the token verifies, whether or not the
     * address belongs to any organization, so the response reveals nothing about it; {@code 400} for a token that
     * is missing or refused, since the route is open to anyone and would otherwise send mail on demand.
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
                                 ? organizationService.sendMembershipsEmail(email)
                                 : Future.failedFuture(new HttpException(400, "Complete the verification and try again")))
                         .onSuccess(v -> ctx.response().setStatusCode(204).end())
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
     * {@code GET /api/auth/org/login/:orgId/providers} — the enabled configurations the organization's
     * login offers, {@code [{id, name, provider}]}: the system's org sign-up registrations followed by
     * the organization's own. {@code 404} for an organization that does not exist.
     */
    private void handleOrganizationProviders(RoutingContext ctx) {
        String orgId = ctx.pathParam("orgId");
        organizationService.findById(orgId)
                           .compose(organization -> {
                               Future<List<OidcConfiguration>> ret;
                               if (organization == null) {
                                   ret = Future.failedFuture(new HttpException(404));
                               } else {
                                   ret = oidcConfigurationService.findEnabledForScope(orgId, null);
                               }
                               return ret;
                           })
                           .onSuccess(configs -> authEndpointSupport.respondProvidersList(ctx, configs))
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
     * member with that email and establishes the browser session. Generic {@code 401} on any failure.
     */
    private void handleLogin(RoutingContext ctx) {
        String orgId = ctx.pathParam("orgId");
        endOffer(ctx);
        authEndpointSupport.handlePasswordLogin(ctx,
                (email, password) -> localAuthenticationService.authenticateOrganizationUser(email, password)
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
     * organization, so its callback finds the one the identity is a member of.
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
     * Signs the identity the callback verified in: to the organization the flow names, or, for a flow the
     * landing page started through one of the system's registrations, to the one organization the identity's
     * organization user is a member of. An organization user that is a member of several is sent back with
     * {@link OidcErrorCodes#MULTIPLE_ORGANIZATIONS} and those organizations, to choose one and sign in at its
     * own login.
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
            Future<OrganizationParticipantIdentity> user = identityService.findOrganizationUserByOidcIdentity(result.config().getId(), sub);
            user.compose(found -> found == null
                        ? Future.succeededFuture(List.<OrganizationMembership>of())
                        : membershipService.findMemberships(found.getId()).map(memberships -> enabledMemberships(found, memberships)))
                .onSuccess(memberships -> signInOrOffer(ctx, result.origin(), user.result(), memberships))
                   .onFailure(err -> authEndpointSupport.completeLogin(ctx, result.origin(), Future.failedFuture(err)));
        }
    }

    // the enabled memberships of organizations the user may sign in to; a user that is disabled, or whose every
    // membership is, has none and is told so, as the organization's own login would tell it
    private static List<OrganizationMembership> enabledMemberships(OrganizationParticipantIdentity user, List<OrganizationMembership> memberships) {
        List<OrganizationMembership> enabled = user.isEnabled() ? memberships.stream().filter(OrganizationMembership::isEnabled).toList() : List.of();
        if (enabled.isEmpty() && !memberships.isEmpty()) {
            throw new OidcCallbackException(OidcErrorCodes.USER_DISABLED);
        }
        return enabled;
    }

    // an organization user that is a member of several organizations has proven who it is, so the landing page may
    // offer those organizations for the person to choose from, where a stranger learns nothing; the page
    // that started the sign-in reads them from handleChoices, so the redirect itself carries no ids
    private void signInOrOffer(RoutingContext ctx, String origin, OrganizationParticipantIdentity user, List<OrganizationMembership> memberships) {
        if (memberships.size() > 1) {
            // read without being removed, since a concurrent request on the session would make the removal
            // unreliable: the offer expires by its timestamp and ends when the page starts an organization's login
            JsonObject offer = new JsonObject()
                    .put("organizations", new JsonArray(memberships.stream().map(OrganizationMembership::getOrganizationId).toList()))
                    .put("at", System.currentTimeMillis());
            ctx.session().put(choicesKey(origin), offer);
            authEndpointSupport.redirectToContinue(ctx, origin, OidcErrorCodes.MULTIPLE_ORGANIZATIONS);
        } else {
            authEndpointSupport.completeLogin(ctx, origin, memberships.isEmpty()
                    ? Future.succeededFuture()
                    : memberOf(user, memberships.getFirst().getOrganizationId()));
        }
    }

    /**
     * Logs the organization user bound to the identity into the organization the flow names. An identity bound to no
     * organization user signs in, through one of the organization's own configurations, the member the organization
     * added under the identity's verified email that nobody has signed in as yet, and is bound to that organization
     * user as its first sign-in method; through a system registration, or for an email that is no such member's,
     * there is no one to sign in.
     */
    private void completeOrganizationLogin(RoutingContext ctx, CallbackResult<OidcConfiguration> result) {
        String sub = authEndpointSupport.verifiedSubject(ctx, result);
        if (sub != null) {
            String email = OAuth2Util.stringClaim(result.claims(), "email");
            Future<Participant> participant = identityService.findOrganizationUserByOidcIdentity(result.config().getId(), sub)
                    .compose(user -> {
                        Future<Participant> ret;
                        if (user != null) {
                            ret = memberOf(user, result.orgId());
                        } else if (result.config() instanceof OwnedOidcConfiguration && email != null) {
                            // only the organization's own directory may claim the member the organization provisioned
                            ret = refusingDisabled(identityService.claimProvisionedOrganizationUser(result.config().getId(), sub, email, result.orgId()));
                        } else {
                            ret = Future.succeededFuture();
                        }
                        return ret;
                    });
            authEndpointSupport.completeLogin(ctx, result.origin(), participant);
        }
    }

    /**
     * The participant {@code user} signs in to organization {@code orgId} as, or {@code null} when it is not a
     * member; a disabled user or membership fails the sign-in with {@link OidcErrorCodes#USER_DISABLED}.
     */
    private Future<Participant> memberOf(OrganizationParticipantIdentity user, String orgId) {
        return user == null ? Future.succeededFuture() : refusingDisabled(membershipService.resolveParticipant(user, orgId));
    }

    // a refused user fails the sign-in with the error code the login page names
    private static Future<Participant> refusingDisabled(Future<Participant> participant) {
        return participant.recover(err -> Future.failedFuture(err instanceof UserDisabledException
                ? new OidcCallbackException(OidcErrorCodes.USER_DISABLED) : err));
    }

}
