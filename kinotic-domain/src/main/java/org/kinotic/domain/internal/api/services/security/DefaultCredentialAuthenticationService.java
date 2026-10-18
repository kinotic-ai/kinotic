package org.kinotic.domain.internal.api.services.security;

import org.kinotic.domain.api.services.security.KinoticJwtIssuer;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.exceptions.AuthenticationException;
import org.kinotic.core.api.security.Participant;
import org.kinotic.domain.api.model.security.identity.MachineParticipantIdentity;
import org.kinotic.domain.api.model.security.identity.ParticipantIdentity;
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
 *       checked in that application; without one, an account's, which acts in the organization the
 *       {@code organizationId} header names, or in the platform when there is none, through its membership
 *       there. Any other {@code clientId} is a {@link MachineParticipantIdentity} id, whose scope comes from
 *       the identity itself and must agree with the scope headers when they are supplied. Both verify the
 *       bcrypt secret hash.</li>
 *   <li><b>Kinotic JWT</b> — {@code Authorization: Bearer <jwt>} header. The JWT was minted by
 *       {@link KinoticJwtIssuer} through one of the OAuth grants. We validate the JWT signature, then resolve
 *       the identity the {@code sub} claim names in the scope the token's {@code organizationId} and
 *       {@code applicationId} claims carry ({@link ParticipantIdentityService#resolveParticipant}), so a token
 *       outliving the membership or scope it was minted for is refused. The scope headers do not select scope
 *       here — the token carries its own.</li>
 * </ol>
 * IdP JWTs are never accepted directly here — the OIDC roundtrip terminates at the gateway,
 * which establishes the browser session the STOMP handshake reads.
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
                                            BiPredicate<ParticipantIdentity, Participant> admitted) {
        return authenticateIdentity(authenticationInfo)
                .compose(authenticated -> {
                    Future<Participant> ret;
                    if (admitted.test(authenticated.identity(), authenticated.participant())) {
                        ret = Future.succeededFuture(authenticated.participant());
                    } else {
                        // valid credentials of an identity another server serves, such as a system
                        // operator's on the management server; the caller gets the answer a wrong password gets
                        log.debug("Credentials of identity {} acting as {} are not admitted by this server",
                                  authenticated.identity().getId(), authenticated.participant());
                        ret = Future.failedFuture(new AuthenticationException("Invalid credentials"));
                    }
                    return ret;
                });
    }

    private Future<AuthenticatedIdentity> authenticateIdentity(Map<String, String> authenticationInfo) {
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
            ret = authenticateKinoticJwt(authHeader.substring(7));
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
     * select, otherwise an account, which then acts through its membership of the organization the header
     * names, or of the platform. Every failure — unknown email, wrong password, disabled account, no
     * password, no membership — is the same generic error, matching the machine path: the upgrade headers
     * get no account oracle.
     */
    private Future<AuthenticatedIdentity> authenticateEmailPassword(String organizationId,
                                                            String applicationId,
                                                            String email,
                                                            String password) {
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            return Future.failedFuture(new AuthenticationException("clientId and clientSecret headers are required for credential authentication"));
        }

        // an account is looked up without scope; the organization header selects its membership
        String userOrganizationId = applicationId != null ? organizationId : null;
        return localAuthenticationService.authenticateLocal(email, password, userOrganizationId, applicationId)
                     .compose(user -> user == null
                             ? Future.succeededFuture()
                             : identityService.resolveParticipant(user.getId(), organizationId, applicationId)
                                              .map(participant -> participant == null ? null : new AuthenticatedIdentity(user, participant)))
                     .compose(authenticated -> authenticated == null
                             ? Future.failedFuture(new AuthenticationException("Invalid credentials"))
                             : Future.succeededFuture(authenticated));
    }

    /**
     * Authenticates a machine by its identity id and provisioned secret. The machine's scope
     * comes from the identity itself; the scope headers, when supplied, must agree with it —
     * a client configured for the wrong organization or application is rejected. Every
     * failure is the same generic error.
     */
    private Future<AuthenticatedIdentity> authenticateMachine(String organizationId,
                                                      String applicationId,
                                                      String clientId,
                                                      String clientSecret) {
        if (clientId == null || clientSecret == null) {
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
                              })
                              .recover(err -> Future.failedFuture(new AuthenticationException("Invalid credentials")));
    }

    /**
     * Validates a Kinotic-issued JWT and resolves it to the participant its {@code sub} acts as in the scope
     * the token's {@code organizationId} / {@code applicationId} claims carry: an existing, enabled identity
     * that may act there, a delegate only while its owner may (defense in depth against a token outliving
     * the membership or scope it was minted for).
     */
    private Future<AuthenticatedIdentity> authenticateKinoticJwt(String token) {
        return jwtIssuer.authenticate(token)
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

    private Future<AuthenticatedIdentity> resolveIdentity(String sub, String jwtOrgId, String jwtAppId) {
        return identityService.findById(sub)
                              .recover(err -> Future.failedFuture(new AuthenticationException("User lookup failed", err)))
                              .compose(identity -> identity == null
                                      ? Future.failedFuture(new AuthenticationException("No user for sub " + sub))
                                      : identityService.resolveParticipant(identity, jwtOrgId, jwtAppId)
                                                       .compose(participant -> {
                                                           Future<AuthenticatedIdentity> ret;
                                                           if (participant == null) {
                                                               // disabled, re-scoped, or no longer a member of the scope
                                                               // the token was minted for. The ids stay in the log; the
                                                               // caller gets none back.
                                                               log.warn("Identity {} may not act in scope {} the JWT names", sub,
                                                                        DomainUtil.describeScope(jwtOrgId, jwtAppId));
                                                               ret = Future.failedFuture(new AuthenticationException("JWT scope does not match user scope"));
                                                           } else {
                                                               ret = Future.succeededFuture(new AuthenticatedIdentity(identity, participant));
                                                           }
                                                           return ret;
                                                       }));
    }
}
