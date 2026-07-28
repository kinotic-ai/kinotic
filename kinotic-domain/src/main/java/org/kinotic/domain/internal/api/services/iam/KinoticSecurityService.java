package org.kinotic.domain.internal.api.services.iam;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.exceptions.AuthenticationException;
import org.kinotic.core.api.security.Participant;
import org.kinotic.core.api.security.SecurityService;
import org.kinotic.domain.api.model.iam.AuthType;
import org.kinotic.domain.api.model.iam.IamUser;
import org.kinotic.domain.api.model.iam.KinoticAudience;
import org.kinotic.domain.api.services.iam.IamUserService;
import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.domain.internal.api.model.IamCredential;
import org.kinotic.domain.internal.api.repositories.IamCredentialRepository;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Sole {@link SecurityService} implementation for Kinotic OS. Handles both email/password
 * and Kinotic-issued JWT authentication across all three scope layers (System, Organization,
 * Application). Scope is identified structurally by the {@code organizationId} and
 * {@code applicationId} STOMP CONNECT headers (or the matching JWT claims):
 * <ul>
 *   <li>both absent → SYSTEM</li>
 *   <li>{@code organizationId} only → ORGANIZATION</li>
 *   <li>both set → APPLICATION</li>
 * </ul>
 * {@code applicationId} without {@code organizationId} is rejected.
 * <p>
 * <b>Two paths:</b>
 * <ol>
 *   <li><b>Client credentials</b> — {@code clientId}/{@code clientSecret} upgrade headers.
 *       Looks up the {@link IamUser} by email + scope, verifies the bcrypt password.</li>
 *   <li><b>Kinotic JWT</b> — {@code Authorization: Bearer <jwt>} header. The JWT was minted
 *       by {@link KinoticJwtIssuer} through one of the OAuth grants. We validate the JWT
 *       signature, then that its audience is the one the calling entry point serves
 *       ({@link #authenticate(Map, KinoticAudience)}; the {@link SecurityService} contract
 *       serves {@link KinoticAudience#PUBLISHED_SERVICES}), then
 *       look up the {@link IamUser} by id from the JWT
 *       {@code sub} claim. When scope headers accompany the token they must match the JWT's
 *       {@code organizationId} / {@code applicationId} claims; a bearer-only request takes
 *       its scope from the signed claims alone.</li>
 * </ol>
 * IdP JWTs are never accepted directly here — the OIDC roundtrip terminates at the gateway,
 * which mints a Kinotic JWT for the STOMP handoff.
 */
@Slf4j
@Component
@RequiredArgsConstructor
// FIXME: this should be in a different module
public class KinoticSecurityService implements SecurityService {

    private final IamUserService userService;
    private final IamCredentialRepository credentialRepository;
    private final KinoticJwtIssuer jwtIssuer;
    private final Vertx vertx;

    @Override
    public Future<Participant> authenticate(Map<String, String> authenticationInfo) {
        return authenticate(authenticationInfo, KinoticAudience.PUBLISHED_SERVICES);
    }

    /**
     * Authenticates for one surface: a Kinotic JWT is accepted only when it was minted for
     * {@code audience}, so a token issued to an MCP host cannot open a STOMP connection and the
     * CLI's token cannot call MCP tools. The credential path presents no token and so no audience.
     * <p>
     * Entry points serving a surface other than {@link KinoticAudience#PUBLISHED_SERVICES} call
     * this directly. Callers pass the audience of the endpoint they serve — never a value read
     * from the request.
     *
     * @param authenticationInfo the request's authentication headers
     * @param audience the surface being authenticated for
     * @return a {@link Future} completing with the {@link Participant}, or failing when
     *         authentication fails or the token was minted for a different surface
     */
    public Future<Participant> authenticate(Map<String, String> authenticationInfo, KinoticAudience audience) {
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

        Future<Participant> ret;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            ret = authenticateKinoticJwt(organizationId, applicationId, authHeader.substring(7), audience);
        } else {
            ret = authenticateEmailPassword(organizationId, applicationId, authInfo);
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
     * Authenticates a user via email and password within the target scope.
     */
    private Future<Participant> authenticateEmailPassword(String organizationId,
                                                          String applicationId,
                                                          Map<String, String> authInfo) {
        String email = authInfo.get("clientId");
        String password = authInfo.get("clientSecret");

        if (email == null || password == null) {
            return Future.failedFuture(new AuthenticationException("clientId and clientSecret headers are required for credential authentication"));
        }

        return Future.fromCompletionStage(userService.findByEmail(email, organizationId, applicationId),
                                          vertx.getOrCreateContext())
                     .compose(user -> {
                         Future<Participant> ret;
                         if (user == null) {
                             ret = Future.failedFuture(new AuthenticationException("Invalid credentials"));
                         } else if (!user.isEnabled()) {
                             ret = Future.failedFuture(new AuthenticationException("User account is disabled"));
                         } else if (user.getAuthType() != AuthType.LOCAL) {
                             ret = Future.failedFuture(new AuthenticationException("User is not a local account"));
                         } else {
                             ret = Future.fromCompletionStage(credentialRepository.findById(user.getId()),
                                                              vertx.getOrCreateContext())
                                         .compose(credential -> verifyPasswordAndCreateParticipant(user, credential, password));
                         }
                         return ret;
                     });
    }

    private Future<Participant> verifyPasswordAndCreateParticipant(IamUser user,
                                                                   IamCredential credential,
                                                                   String password) {
        Future<Participant> ret;
        if (credential == null) {
            ret = Future.failedFuture(new AuthenticationException("Invalid credentials"));
        } else if (!DomainUtil.verifyPassword(password, credential.getPasswordHash())) {
            ret = Future.failedFuture(new AuthenticationException("Invalid credentials"));
        } else {
            ret = Future.succeededFuture(DomainUtil.createParticipant(user));
        }
        return ret;
    }

    /**
     * Validates a Kinotic-issued JWT and resolves it to a Participant. The JWT must: carry the
     * {@code aud} claim for the entry point being called (enforced by
     * {@link KinoticJwtIssuer#authenticate}), so a token minted for MCP tools cannot open a
     * STOMP connection and vice versa; have a {@code sub} claim referencing an existing,
     * enabled {@link IamUser}; and, when the caller supplied {@code organizationId} /
     * {@code applicationId} headers, carry matching claims (defense in depth against a JWT for
     * org A being replayed against org B). A bearer-only request carries no scope headers and
     * authenticates as the scope the JWT's own signed claims declare — the shape MCP hosts and
     * other plain OAuth clients send.
     */
    private Future<Participant> authenticateKinoticJwt(String organizationId,
                                                       String applicationId,
                                                       String token,
                                                       KinoticAudience audience) {
        return jwtIssuer.authenticate(token, audience)
                        .recover(err -> Future.failedFuture(
                                new AuthenticationException("JWT validation failed: " + err.getMessage(), err)))
                        .compose(user -> {
                            JsonObject p = user.principal();
                            String sub = p.getString("sub");
                            String jwtOrgId = p.getString("organizationId");
                            String jwtAppId = p.getString("applicationId");
                            boolean scopeHeadersPresent = organizationId != null || applicationId != null;

                            Future<Participant> ret;
                            if (sub == null) {
                                ret = Future.failedFuture(new AuthenticationException("JWT missing sub claim"));
                            } else if (scopeHeadersPresent
                                    && (!Objects.equals(organizationId, jwtOrgId)
                                            || !Objects.equals(applicationId, jwtAppId))) {
                                ret = Future.failedFuture(new AuthenticationException(
                                        "JWT scope " + describeScope(jwtOrgId, jwtAppId)
                                                + " does not match auth headers " + describeScope(organizationId, applicationId)));
                            } else {
                                // the participant's scope derives from the IamUser record, the same
                                // structure the signed claims were minted from
                                ret = findEnabledUser(sub);
                            }
                            return ret;
                        });
    }

    private Future<Participant> findEnabledUser(String sub) {
        return Future.fromCompletionStage(userService.findById(sub), vertx.getOrCreateContext())
                     .recover(err -> Future.failedFuture(new AuthenticationException("User lookup failed", err)))
                     .compose(iamUser -> {
                         Future<Participant> ret;
                         if (iamUser == null) {
                             ret = Future.failedFuture(new AuthenticationException("No user for sub " + sub));
                         } else if (!iamUser.isEnabled()) {
                             ret = Future.failedFuture(new AuthenticationException("User account is disabled"));
                         } else {
                             ret = Future.succeededFuture(DomainUtil.createParticipant(iamUser));
                         }
                         return ret;
                     });
    }

    private static String describeScope(String organizationId, String applicationId) {
        if (organizationId == null && applicationId == null) return "SYSTEM";
        if (applicationId == null) return "ORGANIZATION/" + organizationId;
        return "APPLICATION/" + organizationId + "/" + applicationId;
    }
}
