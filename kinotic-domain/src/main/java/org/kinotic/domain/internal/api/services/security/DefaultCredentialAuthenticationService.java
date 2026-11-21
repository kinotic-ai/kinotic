package org.kinotic.domain.internal.api.services.security;

import org.kinotic.domain.api.services.security.KinoticJwtIssuer;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.exceptions.AuthenticationException;
import org.kinotic.core.api.security.Participant;
import org.kinotic.core.api.security.AuthenticationContext;
import org.kinotic.domain.api.model.security.identity.MachineParticipantIdentity;
import org.kinotic.domain.api.model.security.identity.ParticipantIdentity;
import org.kinotic.domain.api.model.security.identity.UserParticipantIdentity;
import org.kinotic.domain.api.model.security.participant.ParticipantScope;
import org.kinotic.domain.api.services.security.CredentialAuthenticationService;
import org.kinotic.domain.api.services.security.LocalAuthenticationService;
import org.kinotic.domain.api.services.security.ParticipantIdentityService;
import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.domain.internal.api.model.AuthenticatedIdentity;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.TreeMap;
import java.util.function.BiPredicate;

/**
 * Authenticates email/password, machine and Kinotic-issued JWT credentials to the participant they prove, at every
 * scope layer (System, Organization, Application).
 * <p>
 * <b>Two paths:</b>
 * <ol>
 *   <li><b>Client credentials</b> — {@code clientId}/{@code clientSecret} upgrade headers. A {@code clientId}
 *       containing {@code @} is a user email: with an {@code applicationId} header, an application user's,
 *       checked in that application; with an {@code organizationId} header alone, a user of that organization;
 *       with neither, a system user's. Any other {@code clientId} is a {@link MachineParticipantIdentity} id, whose scope comes from
 *       the identity itself and must agree with the scope headers when they are supplied. Both verify the
 *       bcrypt secret hash.</li>
 *   <li><b>Kinotic JWT</b> — {@code Authorization: Bearer <jwt>} header. The JWT was minted by
 *       {@link KinoticJwtIssuer} through one of the OAuth grants. We validate the JWT signature and that its
 *       {@code aud} names the surface the caller serves, then resolve
 *       the identity the {@code sub} claim names in the scope the token's {@code organizationId} and
 *       {@code applicationId} claims carry ({@link ParticipantIdentityService#resolveParticipant}), so a token
 *       outliving the user or scope it was minted for is refused. The scope headers do not select scope
 *       here — the token carries its own.</li>
 * </ol>
 * IdP JWTs are never accepted directly here — the OIDC roundtrip terminates at the gateway,
 * which establishes the browser session the STOMP handshake reads. The participant that session holds is
 * re-resolved the way a JWT's {@code sub} is, so a session outliving its user or scope is refused.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultCredentialAuthenticationService implements CredentialAuthenticationService {

    private final ParticipantIdentityService identityService;
    private final LocalAuthenticationService localAuthenticationService;
    private final KinoticJwtIssuer jwtIssuer;

    @Override
    public Future<Participant> authenticate(Map<String, String> authenticationInfo,
                                            AuthenticationContext context,
                                            BiPredicate<ParticipantIdentity, Participant> admitted) {
        Validate.notNull(context, "context is required");
        return authenticateIdentity(authenticationInfo, context).compose(authenticated -> admit(authenticated, admitted));
    }

    @Override
    public Future<Participant> reauthenticate(Participant participant,
                                              BiPredicate<ParticipantIdentity, Participant> admitted) {
        Validate.notNull(participant, "participant is required");
        ParticipantScope scope = DomainUtil.scopeOf(participant);
        return resolveIdentity(participant.getId(), scope.organizationId(), scope.applicationId())
                .compose(authenticated -> admit(authenticated, admitted));
    }

    private static Future<Participant> admit(AuthenticatedIdentity authenticated,
                                             BiPredicate<ParticipantIdentity, Participant> admitted) {
        Future<Participant> ret;
        if (admitted.test(authenticated.identity(), authenticated.participant())) {
            ret = Future.succeededFuture(authenticated.participant());
        } else {
            // valid credentials of an identity another server serves, such as a system
            // user's on the management server; the caller gets the answer a wrong password gets
            log.debug("Credentials of identity {} acting as {} are not admitted by this server",
                      authenticated.identity().getId(), authenticated.participant());
            ret = Future.failedFuture(new AuthenticationException("Invalid credentials"));
        }
        return ret;
    }

    private Future<AuthenticatedIdentity> authenticateIdentity(Map<String, String> authenticationInfo, AuthenticationContext context) {
        // HTTP callers (AuthenticationHandler) lowercase all header names; STOMP preserves case.
        // Wrap in a case-insensitive view so both transports work with the same camelCase names.
        Map<String, String> authInfo = caseInsensitive(authenticationInfo);

        String organizationId = authInfo.get("organizationId");
        String applicationId = authInfo.get("applicationId");

        if (applicationId != null && organizationId == null) {
            return Future.failedFuture(new AuthenticationException(
                    "organizationId header is required when applicationId is supplied"));
        }

        String authHeader = authInfo.get("Authorization");
        String clientId = authInfo.get("clientId");
        String clientSecret = authInfo.get("clientSecret");

        Future<AuthenticatedIdentity> ret;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            ret = authenticateKinoticJwt(authHeader.substring(7), context);
        } else if (clientId != null || clientSecret != null) {
            // an email is a user credential; anything else is a machine identity id
            if (clientId != null && clientId.indexOf('@') >= 0) {
                ret = authenticateEmailPassword(organizationId, applicationId, clientId, clientSecret);
            } else {
                ret = authenticateMachine(organizationId, applicationId, clientId, clientSecret);
            }
        } else {
            // MCP hosts probe POST /mcp with no credentials to collect the RFC 9728 challenge, so
            // this is a routine step of the OAuth flow rather than a malformed credential attempt
            ret = Future.failedFuture(new AuthenticationException(
                    "Authorization Bearer token, or clientId and clientSecret headers, are required"));
        }
        return ret;
    }

    private static Map<String, String> caseInsensitive(Map<String, String> source) {
        Map<String, String> ci = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        if (source != null) {
            ci.putAll(source);
        }
        return ci;
    }

    /**
     * Authenticates a user via email and password: an application's user within the application the headers
     * select, a user of the organization the header names, or a system user when the headers name no scope; an
     * organization's or an application's login must take a password. Every failure — unknown email, wrong password,
     * disabled user, no password, a login
     * that takes no password — is the same generic error, matching the machine path: the
     * upgrade headers tell no one which emails have users.
     */
    private Future<AuthenticatedIdentity> authenticateEmailPassword(String organizationId,
                                                            String applicationId,
                                                            String email,
                                                            String password) {
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            return Future.failedFuture(new AuthenticationException("clientId and clientSecret headers are required for credential authentication"));
        }

        Future<? extends UserParticipantIdentity> verified;
        if (applicationId != null) {
            verified = localAuthenticationService.authenticateApplicationUser(email, password, organizationId, applicationId);
        } else if (organizationId != null) {
            verified = localAuthenticationService.authenticateOrganizationUser(email, password, organizationId);
        } else {
            verified = localAuthenticationService.authenticateSystemUser(email, password);
        }
        return verified
                     .compose(user -> user == null
                             ? Future.succeededFuture()
                             : identityService.resolveParticipant(user, organizationId, applicationId)
                                              .map(participant -> participant == null ? null : new AuthenticatedIdentity(user, participant)))
                     .compose(authenticated -> authenticated == null
                             ? Future.failedFuture(new AuthenticationException("Invalid credentials"))
                             : Future.succeededFuture(authenticated));
    }

    /**
     * Authenticates a machine by its identity id and provisioned secret. The machine's scope
     * comes from the identity itself; the scope headers, when supplied, must agree with it —
     * a client configured for the wrong organization or application is rejected. Every
     * rejection is the same generic error; a failure of the stores behind the checks is not a rejection and
     * fails the authentication as itself.
     */
    private Future<AuthenticatedIdentity> authenticateMachine(String organizationId,
                                                      String applicationId,
                                                      String clientId,
                                                      String clientSecret) {
        if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
            return Future.failedFuture(new AuthenticationException(
                    "clientId and clientSecret headers are required for credential authentication"));
        }
        return identityService.verifyMachineCredentials(clientId, clientSecret)
                              .compose(machine -> {
                                  Future<AuthenticatedIdentity> ret;
                                  if ((organizationId != null && !organizationId.equals(machine.getOrganizationId()))
                                          || (applicationId != null && !applicationId.equals(machine.getApplicationId()))) {
                                      ret = Future.failedFuture(new AuthenticationException("Invalid credentials"));
                                  } else {
                                      ret = Future.succeededFuture(new AuthenticatedIdentity(machine, DomainUtil.createParticipant(machine)));
                                  }
                                  return ret;
                              });
    }

    /**
     * Validates a Kinotic-issued JWT, minted for the surface {@code context} names, and resolves it to the participant its
     * {@code sub} acts as in the scope the token's {@code organizationId} / {@code applicationId} claims carry: an
     * existing, enabled identity that may act there, a delegate only while its owner may (defense in depth
     * against a token outliving the user or scope it was minted for).
     */
    private Future<AuthenticatedIdentity> authenticateKinoticJwt(String token, AuthenticationContext context) {
        return jwtIssuer.authenticate(token, context)
                        .recover(err -> Future.failedFuture(
                                new AuthenticationException("JWT validation failed: " + err.getMessage(), err)))
                        .compose(user -> {
                            JsonObject p = user.principal();
                            String sub = p.getString("sub");

                            Future<AuthenticatedIdentity> ret;
                            if (sub == null) {
                                ret = Future.failedFuture(new AuthenticationException("JWT missing sub claim"));
                            } else {
                                ret = resolveIdentity(sub,
                                                      p.getString("organizationId"),
                                                      p.getString("applicationId"));
                            }
                            return ret;
                        });
    }

    // resolves the identity a token's sub or a session's participant names, in the scope it was authenticated for
    private Future<AuthenticatedIdentity> resolveIdentity(String identityId, String organizationId, String applicationId) {
        // a failure of the identity store is not a rejection and fails the authentication as itself
        return identityService.findById(identityId)
                              .compose(identity -> identity == null
                                      ? Future.failedFuture(new AuthenticationException("No identity " + identityId))
                                      : identityService.resolveParticipant(identity, organizationId, applicationId)
                                                       .compose(participant -> {
                                                           Future<AuthenticatedIdentity> ret;
                                                           if (participant == null) {
                                                               // disabled, re-scoped, or no longer a member of the scope
                                                               // it was authenticated for. The ids stay in the log; the
                                                               // caller gets none back.
                                                               log.warn("Identity {} may not act in scope {}", identityId,
                                                                        DomainUtil.describeScope(organizationId, applicationId));
                                                               ret = Future.failedFuture(new AuthenticationException("The identity may not act in the scope it was authenticated for"));
                                                           } else {
                                                               ret = Future.succeededFuture(new AuthenticatedIdentity(identity, participant));
                                                           }
                                                           return ret;
                                                       }));
    }
}
