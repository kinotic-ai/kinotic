package org.kinotic.os.internal.api.services.iam;

import io.vertx.core.json.JsonObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.exceptions.AuthenticationException;
import org.kinotic.core.api.security.DefaultParticipant;
import org.kinotic.core.api.security.Participant;
import org.kinotic.core.api.security.ParticipantConstants;
import org.kinotic.core.api.security.SecurityService;
import org.kinotic.core.internal.security.KinoticJwtIssuer;
import org.kinotic.os.api.model.iam.AuthType;
import org.kinotic.os.api.model.iam.IamUser;
import org.kinotic.os.api.utils.DomainUtil;
import org.kinotic.os.internal.api.model.iam.IamCredential;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;

/**
 * Sole {@link SecurityService} implementation for Kinotic OS. Handles both email/password
 * and Kinotic-issued JWT authentication across all three scope layers (System, Organization,
 * Application).
 * <p>
 * <b>Two paths:</b>
 * <ol>
 *   <li><b>Email/password</b> — direct STOMP CONNECT with {@code login}/{@code passcode}
 *       headers. Looks up the {@link IamUser} by email + scope, verifies the bcrypt password.</li>
 *   <li><b>Kinotic JWT</b> — {@code Authorization: Bearer <jwt>} header. The JWT was minted
 *       by {@link KinoticJwtIssuer} after a successful OIDC callback (via the gateway's
 *       {@code /api/login/callback} or {@code /api/signup/complete-org}). We validate the
 *       JWT signature + audience, then look up the {@link IamUser} by id from the JWT
 *       {@code sub} claim. Cross-checks that the JWT's scope matches the headers.</li>
 * </ol>
 * IdP JWTs are never accepted directly here — the OIDC roundtrip terminates at the gateway,
 * which mints a Kinotic JWT for the STOMP handoff.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IamSecurityService implements SecurityService {

    private final DefaultIamUserService userService;
    private final IamCredentialService credentialService;
    private final KinoticJwtIssuer jwtIssuer;

    /**
     * Entry point for all authentication. Parses the {@code authScopeType}/{@code authScopeId}
     * headers to determine the target scope, then dispatches to either Kinotic JWT or
     * email/password authentication based on the presence of a Bearer token.
     *
     * @param authenticationInfo headers from the STOMP CONNECT frame. Required:
     *                          {@code authScopeType}, {@code authScopeId} (e.g., "kinotic" for SYSTEM).
     *                          For email/password: {@code login}, {@code passcode}.
     *                          For JWT: {@code Authorization: Bearer <jwt>}.
     */
    @Override
    public CompletableFuture<Participant> authenticate(Map<String, String> authenticationInfo) {
        // HTTP callers (AuthenticationHandler) lowercase all header names; STOMP preserves case.
        // Wrap in a case-insensitive view so both transports work with the same camelCase names.
        Map<String, String> authInfo = caseInsensitive(authenticationInfo);

        String authScopeType = authInfo.get("authScopeType");
        String authScopeId = authInfo.get("authScopeId");

        if (authScopeType == null) {
            return CompletableFuture.failedFuture(new AuthenticationException("authScopeType header is required"));
        }
        if (authScopeId == null) {
            return CompletableFuture.failedFuture(new AuthenticationException("authScopeId header is required"));
        }

        String authHeader = authInfo.get("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authenticateKinoticJwt(authScopeType, authScopeId, authHeader.substring(7));
        } else {
            return authenticateEmailPassword(authScopeType, authScopeId, authInfo);
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
    private CompletableFuture<Participant> authenticateEmailPassword(String authScopeType,
                                                                     String authScopeId,
                                                                     Map<String, String> authInfo) {
        String email = authInfo.get("login");
        String password = authInfo.get("passcode");

        if (email == null || password == null) {
            return CompletableFuture.failedFuture(new AuthenticationException("login and passcode headers are required for email/password authentication"));
        }

        return userService.findByEmailAndScope(email, authScopeType, authScopeId)
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
                              return credentialService.findById(user.getId())
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
        return CompletableFuture.completedFuture(createParticipantFromUser(user));
    }

    /**
     * Validates a Kinotic-issued JWT and resolves it to a Participant. The JWT must:
     * carry {@code aud=kinotic} (enforced by {@link KinoticJwtIssuer#authenticate}); have
     * a {@code sub} claim referencing an existing, enabled {@link IamUser}; and carry
     * {@code scopeType}/{@code scopeId} claims that match the auth headers (defense in depth
     * against a JWT for org A being replayed against org B).
     */
    private CompletableFuture<Participant> authenticateKinoticJwt(String authScopeType,
                                                                  String authScopeId,
                                                                  String token) {
        CompletableFuture<Participant> result = new CompletableFuture<>();
        jwtIssuer.authenticate(token)
                 .onSuccess(user -> {
                     JsonObject p = user.principal();
                     String sub = p.getString("sub");
                     String jwtScopeType = p.getString("scopeType");
                     String jwtScopeId = p.getString("scopeId");

                     if (sub == null) {
                         result.completeExceptionally(new AuthenticationException("JWT missing sub claim"));
                         return;
                     }
                     if (jwtScopeType == null || jwtScopeId == null) {
                         result.completeExceptionally(new AuthenticationException("JWT missing scope claims"));
                         return;
                     }
                     if (!authScopeType.equals(jwtScopeType) || !authScopeId.equals(jwtScopeId)) {
                         result.completeExceptionally(new AuthenticationException(
                                 "JWT scope " + jwtScopeType + "/" + jwtScopeId
                                         + " does not match auth headers " + authScopeType + "/" + authScopeId));
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
                             result.complete(createParticipantFromUser(iamUser));
                         }
                     });
                 })
                 .onFailure(err -> result.completeExceptionally(
                         new AuthenticationException("JWT validation failed: " + err.getMessage(), err)));
        return result;
    }

    private Participant createParticipantFromUser(IamUser user) {
        Map<String, String> metadata = new HashMap<>(Map.of(
                ParticipantConstants.PARTICIPANT_TYPE_METADATA_KEY, ParticipantConstants.PARTICIPANT_TYPE_USER,
                "email", user.getEmail(),
                "displayName", user.getDisplayName() != null ? user.getDisplayName() : user.getEmail(),
                "authType", user.getAuthType().name()
        ));

        // tenantId is the client-tenant the caller is acting within — meaningful only for
        // APPLICATION-scoped users (where it partitions SHARED entity data). SYSTEM and ORGANIZATION
        // identities are not tenants, so user.getTenantId() must be null for them.
        return new DefaultParticipant(user.getTenantId(), user.getId(),
                user.getAuthScopeType(), user.getAuthScopeId(), metadata, List.of());
    }
}
