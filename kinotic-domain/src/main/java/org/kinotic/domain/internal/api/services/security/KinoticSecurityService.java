package org.kinotic.domain.internal.api.services.security;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.exceptions.AuthenticationException;
import org.kinotic.core.api.security.Participant;
import org.kinotic.core.api.security.SecurityService;
import org.kinotic.domain.api.model.security.identity.DelegatingParticipantIdentity;
import org.kinotic.domain.api.model.security.identity.MachineParticipantIdentity;
import org.kinotic.domain.api.model.security.identity.ParticipantIdentity;
import org.kinotic.domain.api.services.security.LocalAuthenticationService;
import org.kinotic.domain.api.services.security.ParticipantIdentityService;
import org.kinotic.domain.api.utils.DomainUtil;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Sole {@link SecurityService} implementation for Kinotic OS. Handles both email/password
 * and Kinotic-issued JWT authentication across all three scope layers (System, Organization,
 * Application). Scope is identified structurally by the {@code organizationId} and
 * {@code applicationId} of the resolved {@link ParticipantIdentity}:
 * <ul>
 *   <li>both absent → SYSTEM</li>
 *   <li>{@code organizationId} only → ORGANIZATION</li>
 *   <li>both set → APPLICATION</li>
 * </ul>
 * {@code applicationId} without {@code organizationId} is rejected.
 * <p>
 * <b>Two paths:</b>
 * <ol>
 *   <li><b>Client credentials</b> — {@code clientId}/{@code clientSecret} upgrade headers. A
 *       {@code clientId} containing {@code @} is a user email, looked up within the scope the
 *       {@code organizationId} / {@code applicationId} headers select; any other
 *       {@code clientId} is a {@link MachineParticipantIdentity} id, whose scope comes from
 *       the identity itself and must agree with the scope headers when they are supplied.
 *       Both verify the bcrypt secret hash.</li>
 *   <li><b>Kinotic JWT</b> — {@code Authorization: Bearer <jwt>} header. The JWT was minted
 *       by {@link KinoticJwtIssuer} through one of the OAuth grants. We validate the JWT
 *       signature, then look up the {@link ParticipantIdentity} by id from the JWT {@code sub} claim and
 *       require the user's {@code organizationId} / {@code applicationId} to equal the token's
 *       claims. The scope headers do not select scope here — the token carries its own.</li>
 * </ol>
 * IdP JWTs are never accepted directly here — the OIDC roundtrip terminates at the gateway,
 * which establishes the browser session the STOMP handshake reads.
 */
@Slf4j
@Component
@RequiredArgsConstructor
// FIXME: this should be in a different module
public class KinoticSecurityService implements SecurityService {

    private final ParticipantIdentityService identityService;
    private final LocalAuthenticationService localAuthenticationService;
    private final KinoticJwtIssuer jwtIssuer;

    @Override
    public Future<Participant> authenticate(Map<String, String> authenticationInfo) {
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

        Future<Participant> ret;
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
     * Authenticates a user via email and password within the target scope. Every failure —
     * unknown email, wrong password, disabled account, non-local account — is the same
     * generic error, matching the machine path: the upgrade headers get no account oracle.
     */
    private Future<Participant> authenticateEmailPassword(String organizationId,
                                                          String applicationId,
                                                          String email,
                                                          String password) {
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            return Future.failedFuture(new AuthenticationException("clientId and clientSecret headers are required for credential authentication"));
        }

        return localAuthenticationService.authenticateLocal(email, password, organizationId, applicationId)
                     .compose(user -> {
                         Future<Participant> ret;
                         if (user == null) {
                             ret = Future.failedFuture(new AuthenticationException("Invalid credentials"));
                         } else {
                             ret = Future.succeededFuture(DomainUtil.createParticipant(user));
                         }
                         return ret;
                     });
    }

    /**
     * Authenticates a machine by its identity id and provisioned secret. The machine's scope
     * comes from the identity itself; the scope headers, when supplied, must agree with it —
     * a client configured for the wrong organization or application is rejected. Every
     * failure is the same generic error.
     */
    private Future<Participant> authenticateMachine(String organizationId,
                                                    String applicationId,
                                                    String clientId,
                                                    String clientSecret) {
        if (clientId == null || clientSecret == null) {
            return Future.failedFuture(new AuthenticationException(
                    "clientId and clientSecret headers are required for credential authentication"));
        }
        return identityService.verifyMachineCredentials(clientId, clientSecret)
                              .compose(machine -> {
                                  Future<Participant> ret;
                                  if ((organizationId != null && !organizationId.equals(machine.getOrganizationId()))
                                          || (applicationId != null && !applicationId.equals(machine.getApplicationId()))) {
                                      ret = Future.failedFuture(new AuthenticationException("Invalid credentials"));
                                  } else {
                                      ret = Future.succeededFuture(DomainUtil.createParticipant(machine));
                                  }
                                  return ret;
                              })
                              .recover(err -> Future.failedFuture(new AuthenticationException("Invalid credentials")));
    }

    /**
     * Validates a Kinotic-issued JWT and resolves it to a Participant. The JWT must have a
     * {@code sub} claim referencing an existing, enabled {@link ParticipantIdentity} whose
     * {@code organizationId} / {@code applicationId} equal the token's claims for those values
     * (defense in depth against a token outliving the scope it was minted for). The resulting
     * Participant is scoped by that user record.
     */
    private Future<Participant> authenticateKinoticJwt(String token) {
        return jwtIssuer.authenticate(token)
                        .recover(err -> Future.failedFuture(
                                new AuthenticationException("JWT validation failed: " + err.getMessage(), err)))
                        .compose(user -> {
                            JsonObject p = user.principal();
                            String sub = p.getString("sub");

                            Future<Participant> ret;
                            if (sub == null) {
                                ret = Future.failedFuture(new AuthenticationException("JWT missing sub claim"));
                            } else {
                                ret = resolveParticipant(sub,
                                                         p.getString("organizationId"),
                                                         p.getString("applicationId"));
                            }
                            return ret;
                        });
    }

    private Future<Participant> resolveParticipant(String sub, String jwtOrgId, String jwtAppId) {
        return identityService.findById(sub)
                              .recover(err -> Future.failedFuture(new AuthenticationException("User lookup failed", err)))
                              .compose(identity -> {
                                  Future<Participant> ret;
                                  if (identity == null) {
                                      ret = Future.failedFuture(new AuthenticationException("No user for sub " + sub));
                                  } else if (!identity.isEnabled()) {
                                      ret = Future.failedFuture(new AuthenticationException("User account is disabled"));
                                  } else if (!Objects.equals(identity.getOrganizationId(), jwtOrgId)
                                          || !Objects.equals(identity.getApplicationId(), jwtAppId)) {
                                      // the user was moved or re-scoped after the token was minted, so the
                                      // signed claims no longer describe the authority the record grants.
                                      // The scopes stay in the log; the caller gets no ids back.
                                      log.warn("JWT scope {} does not match scope {} of user {}",
                                               DomainUtil.describeScope(jwtOrgId, jwtAppId),
                                               DomainUtil.describeScope(identity.getOrganizationId(), identity.getApplicationId()),
                                               sub);
                                      ret = Future.failedFuture(new AuthenticationException("JWT scope does not match user scope"));
                                  } else if (identity instanceof DelegatingParticipantIdentity delegate) {
                                      // a delegate wields its owner's authority, so revoking the owner
                                      // revokes every delegate on the next request, with no cascade to miss
                                      ret = requireEnabledOwner(delegate)
                                              .map(v -> DomainUtil.createParticipant(delegate));
                                  } else {
                                      ret = Future.succeededFuture(DomainUtil.createParticipant(identity));
                                  }
                                  return ret;
                              });
    }

    private Future<Void> requireEnabledOwner(DelegatingParticipantIdentity delegate) {
        return identityService.findById(delegate.getOwnerId())
                              .recover(err -> Future.failedFuture(new AuthenticationException("Owner lookup failed", err)))
                              .compose(owner -> {
                                  Future<Void> ret;
                                  if (owner == null || !owner.isEnabled()) {
                                      log.warn("Delegate {} rejected: owner {} is missing or disabled",
                                               delegate.getId(), delegate.getOwnerId());
                                      ret = Future.failedFuture(new AuthenticationException("Delegate owner is not available"));
                                  } else {
                                      ret = Future.succeededFuture();
                                  }
                                  return ret;
                              });
    }
}
