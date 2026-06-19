package org.kinotic.os.internal.api.services;

import io.vertx.core.json.JsonObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.exceptions.AuthenticationException;
import org.kinotic.core.api.security.Participant;
import org.kinotic.core.api.security.SecurityService;
import org.kinotic.domain.api.model.iam.AuthType;
import org.kinotic.domain.api.model.iam.IamUser;
import org.kinotic.domain.api.services.iam.IamUserService;
import org.kinotic.domain.internal.utils.DomainUtil;
import org.kinotic.domain.internal.api.model.IamCredential;
import org.kinotic.domain.internal.api.repositories.IamCredentialRepository;
import org.kinotic.os.internal.api.services.iam.KinoticJwtIssuer;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;

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
 *       by {@link KinoticJwtIssuer} after a successful OIDC callback. We validate the JWT
 *       signature + audience, then look up the {@link IamUser} by id from the JWT
 *       {@code sub} claim. Cross-checks that the JWT's {@code organizationId} /
 *       {@code applicationId} claims match the headers (defense in depth against a JWT for
 *       org A being replayed against org B).</li>
 * </ol>
 * IdP JWTs are never accepted directly here — the OIDC roundtrip terminates at the gateway,
 * which mints a Kinotic JWT for the STOMP handoff.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KinoticSecurityService implements SecurityService {

    private final IamUserService userService;
    private final IamCredentialRepository credentialRepository;
    private final KinoticJwtIssuer jwtIssuer;

    @Override
    public CompletableFuture<Participant> authenticate(Map<String, String> authenticationInfo) {
        // HTTP callers (AuthenticationHandler) lowercase all header names; STOMP preserves case.
        // Wrap in a case-insensitive view so both transports work with the same camelCase names.
        Map<String, String> authInfo = caseInsensitive(authenticationInfo);

        String organizationId = authInfo.get("organizationId");
        String applicationId = authInfo.get("applicationId");

        if (applicationId != null && organizationId == null) {
            return CompletableFuture.failedFuture(new AuthenticationException(
                    "organizationId header is required when applicationId is supplied"));
        }

        String authHeader = authInfo.get("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authenticateKinoticJwt(organizationId, applicationId, authHeader.substring(7));
        } else {
            return authenticateEmailPassword(organizationId, applicationId, authInfo);
        }
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
    private CompletableFuture<Participant> authenticateEmailPassword(String organizationId,
                                                                     String applicationId,
                                                                     Map<String, String> authInfo) {
        String email = authInfo.get("clientId");
        String password = authInfo.get("clientSecret");

        if (email == null || password == null) {
            return CompletableFuture.failedFuture(new AuthenticationException("clientId and clientSecret headers are required for credential authentication"));
        }

        return userService.findByEmail(email, organizationId, applicationId)
                          .thenCompose(user -> {
                              if (user == null) {
                                  return CompletableFuture.failedFuture(new AuthenticationException("Invalid credentials"));
                              }
                              if (!user.isEnabled()) {
                                  return CompletableFuture.failedFuture(new AuthenticationException("User account is disabled"));
                              }
                              if (user.getAuthType() != AuthType.LOCAL) {
                                  return CompletableFuture.failedFuture(new AuthenticationException("User is not a local account"));
                              }
                              return credentialRepository.findById(user.getId())
                                                      .thenCompose(credential -> verifyPasswordAndCreateParticipant(user, credential, password));
                          });
    }

    private CompletableFuture<Participant> verifyPasswordAndCreateParticipant(IamUser user,
                                                                              IamCredential credential,
                                                                              String password) {
        if (credential == null) {
            return CompletableFuture.failedFuture(new AuthenticationException("Invalid credentials"));
        }
        if (!DomainUtil.verifyPassword(password, credential.getPasswordHash())) {
            return CompletableFuture.failedFuture(new AuthenticationException("Invalid credentials"));
        }
        return CompletableFuture.completedFuture(DomainUtil.createParticipant(user));
    }

    /**
     * Validates a Kinotic-issued JWT and resolves it to a Participant. The JWT must:
     * carry {@code aud=kinotic} (enforced by {@link KinoticJwtIssuer#authenticate}); have
     * a {@code sub} claim referencing an existing, enabled {@link IamUser}; and carry
     * {@code organizationId} / {@code applicationId} claims that match the auth headers
     * (defense in depth against a JWT for org A being replayed against org B).
     */
    private CompletableFuture<Participant> authenticateKinoticJwt(String organizationId,
                                                                  String applicationId,
                                                                  String token) {
        CompletableFuture<Participant> result = new CompletableFuture<>();
        jwtIssuer.authenticate(token)
                 .onSuccess(user -> {
                     JsonObject p = user.principal();
                     String sub = p.getString("sub");
                     String jwtOrgId = p.getString("organizationId");
                     String jwtAppId = p.getString("applicationId");

                     if (sub == null) {
                         result.completeExceptionally(new AuthenticationException("JWT missing sub claim"));
                         return;
                     }
                     if (!Objects.equals(organizationId, jwtOrgId) || !Objects.equals(applicationId, jwtAppId)) {
                         result.completeExceptionally(new AuthenticationException(
                                 "JWT scope " + describeScope(jwtOrgId, jwtAppId)
                                         + " does not match auth headers " + describeScope(organizationId, applicationId)));
                         return;
                     }
                     userService.findById(sub).whenComplete((iamUser, err) -> {
                         if (err != null) {
                             result.completeExceptionally(new AuthenticationException("User lookup failed", err));
                         } else if (iamUser == null) {
                             result.completeExceptionally(new AuthenticationException("No user for sub " + sub));
                         } else if (!iamUser.isEnabled()) {
                             result.completeExceptionally(new AuthenticationException("User account is disabled"));
                         } else {
                             result.complete(DomainUtil.createParticipant(iamUser));
                         }
                     });
                 })
                 .onFailure(err -> result.completeExceptionally(
                         new AuthenticationException("JWT validation failed: " + err.getMessage(), err)));
        return result;
    }

    private static String describeScope(String organizationId, String applicationId) {
        if (organizationId == null && applicationId == null) return "SYSTEM";
        if (applicationId == null) return "ORGANIZATION/" + organizationId;
        return "APPLICATION/" + organizationId + "/" + applicationId;
    }
}
