package org.kinotic.os.internal.api.services.iam;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Jwk;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.exceptions.AuthenticationException;
import org.kinotic.core.api.security.DefaultParticipant;
import org.kinotic.core.api.security.Participant;
import org.kinotic.core.api.security.ParticipantConstants;
import org.kinotic.core.api.security.SecurityService;
import org.kinotic.core.internal.api.security.JwksService;
import org.kinotic.os.api.model.iam.AuthType;
import org.kinotic.os.api.model.iam.IamUser;
import org.kinotic.os.api.model.iam.OidcConfiguration;
import org.kinotic.os.api.utils.DomainUtil;
import org.kinotic.os.internal.api.model.iam.IamCredential;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.security.PublicKey;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Sole {@link SecurityService} implementation for Kinotic OS. Handles both email/password
 * and OIDC authentication across all three scope layers (System, Organization, Application).
 * <p>
 * <b>Authentication is two-phase:</b>
 * <ol>
 *   <li><b>Identity verification</b> — prove the caller is who they claim to be
 *       (password check or JWT signature validation)</li>
 *   <li><b>Scope authorization</b> — confirm a pre-created {@link IamUser} exists in the
 *       target scope. A valid Google token alone does not grant access — the user must have
 *       been explicitly created in the specific scope (application, organization, or system)
 *       by an administrator.</li>
 * </ol>
 * This two-phase design means the same OIDC provider (e.g., one Google registration) can be
 * shared across many applications without users in one application automatically gaining
 * access to another. The JWT proves identity; the scoped user lookup proves authorization
 * to access that particular scope.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IamSecurityService implements SecurityService {

    private final DefaultIamUserService userService;
    private final IamCredentialService credentialService;
    private final OidcConfigLookup oidcConfigLookup;
    private final JwksService jwksService;

    /**
     * Entry point for all authentication. Parses the {@code authScopeType} header to determine
     * the target scope, then dispatches to either OIDC or email/password authentication based
     * on the presence of a Bearer token in the Authorization header.
     *
     * @param authenticationInfo headers from the STOMP CONNECT frame. Required: {@code authScopeType}.
     *                          For email/password: {@code login}, {@code passcode}.
     *                          For OIDC: {@code Authorization: Bearer <jwt>}.
     *                          Required: {@code authScopeId} (e.g., "kinotic" for SYSTEM).
     */
    @Override
    public CompletableFuture<Participant> authenticate(Map<String, String> authenticationInfo) {
        // HTTP callers (AuthenticationHandler) lowercase all header names; STOMP preserves case.
        // Wrap the incoming map in a case-insensitive view so both transports work with the same
        // camelCase header names throughout this class.
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
            return authenticateOidc(authScopeType, authScopeId, authHeader);
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
     * <p>
     * Flow:
     * <ol>
     *   <li>Look up IamUser by email + scope — fails if no pre-created user exists</li>
     *   <li>Verify the user is enabled and is a LOCAL auth type</li>
     *   <li>Retrieve the IamCredential and verify the bcrypt password hash</li>
     *   <li>Return a Participant with the user's identity and scope as tenant context</li>
     * </ol>
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

    private Participant createParticipantFromUser(IamUser user) {
        Map<String, String> metadata = new HashMap<>(Map.of(
                ParticipantConstants.PARTICIPANT_TYPE_METADATA_KEY, ParticipantConstants.PARTICIPANT_TYPE_USER,
                "email", user.getEmail(),
                "displayName", user.getDisplayName() != null ? user.getDisplayName() : user.getEmail(),
                "authType", user.getAuthType().name()
        ));

        // tenantId is the client-tenant the caller is acting within — it is NOT the same as the
        // auth scope id (which says which org/application the user authenticated under). Leave it
        // null here; per-request mechanisms are responsible for populating it when applicable.
        return new DefaultParticipant(null, user.getId(),
                user.getAuthScopeType(), user.getAuthScopeId(), metadata, List.of());
    }

    /**
     * Initiates OIDC authentication by loading the OIDC configurations enabled for the target
     * scope, fetching the JWKS key for the token, and delegating to {@link #validateOidcToken}.
     */
    private CompletableFuture<Participant> authenticateOidc(String authScopeType,
                                                            String authScopeId,
                                                            String authHeader) {
        String token = authHeader.substring(7); // Strip "Bearer "

        return oidcConfigLookup.getConfigsForScope(authScopeType, authScopeId)
                               .thenCompose(configs -> {
                                   if (configs == null || configs.isEmpty()) {
                                       return CompletableFuture.failedFuture(
                                               new AuthenticationException("No OIDC configurations found for scope " + authScopeType + "/" + authScopeId));
                                   }
                                   return jwksService.getKeyFromToken(token)
                                                     .thenCompose(jwk -> validateOidcToken(token, jwk, configs, authScopeType, authScopeId));
                               });
    }

    /**
     * Validates an OIDC JWT token and resolves it to a Participant within the target scope.
     * <p>
     * This method performs identity verification AND scope authorization as separate steps:
     * <ol>
     *   <li><b>Verify JWT signature</b> using the JWKS public key</li>
     *   <li><b>Extract email</b> from standard claims (email, preferred_username, sub, upn, unique_name)</li>
     *   <li><b>Match OIDC config</b> — find which of the scope's enabled configurations matches
     *       the token's issuer and the user's email domain</li>
     *   <li><b>Validate audience</b> — ensure the token's {@code aud} claim matches the config's expected audience</li>
     *   <li><b>Validate expiration</b></li>
     *   <li><b>Look up IamUser by email + scope</b> — this is the scope authorization gate.
     *       A valid JWT from a trusted provider is not enough; a user record must have been
     *       pre-created in this specific scope by an administrator. This prevents a user who
     *       is signed up for Application A from using the same Google token to access Application B.</li>
     *   <li><b>Link OIDC identity on first login</b> — if the user's oidcSubject is not yet set,
     *       populate it from the JWT's {@code sub} claim so subsequent logins can be correlated</li>
     * </ol>
     */
    private CompletableFuture<Participant> validateOidcToken(String token,
                                                             Jwk<? extends Key> jwk,
                                                             List<OidcConfiguration> configs,
                                                             String authScopeType,
                                                             String authScopeId) {
        try {
            Key key = jwk.toKey();
            if (!(key instanceof PublicKey publicKey)) {
                return CompletableFuture.failedFuture(new AuthenticationException("Jwk does not contain a PublicKey instance"));
            }

            Claims claims = Jwts.parser()
                                .verifyWith(publicKey)
                                .build()
                                .parseSignedClaims(token)
                                .getPayload();

            // Extract email from standard claims
            String email = extractEmail(claims);
            if (email == null) {
                return CompletableFuture.failedFuture(new AuthenticationException("No email found in JWT claims"));
            }

            // Match issuer + email domain against available OIDC configs
            String issuer = claims.getIssuer();
            String emailDomain = email.contains("@") ? email.split("@")[1] : null;
            OidcConfiguration matchedConfig = matchOidcConfig(configs, issuer, emailDomain);
            if (matchedConfig == null) {
                return CompletableFuture.failedFuture(new AuthenticationException("No matching OIDC configuration for issuer: " + issuer));
            }

            // Validate audience
            Set<String> audiences = claims.getAudience();
            if (matchedConfig.getAudience() != null && !matchedConfig.getAudience().isEmpty()) {
                if (audiences == null || audiences.stream().noneMatch(a -> matchedConfig.getAudience().equals(a))) {
                    return CompletableFuture.failedFuture(new AuthenticationException("Invalid audience: " + audiences));
                }
            }

            // Validate expiration
            if (claims.getExpiration() != null && claims.getExpiration().before(Date.from(Instant.now()))) {
                return CompletableFuture.failedFuture(new AuthenticationException("Token has expired"));
            }

            // Extract roles if configured
            List<String> roles = extractRoles(matchedConfig, claims);

            // Look up existing user by oidcSubject + scope
            String subject = claims.getSubject();
            return lookupOrProvisionOidcUser(subject, email, matchedConfig, authScopeType, authScopeId, claims)
                    .thenApply(user -> createOidcParticipant(user, roles, claims));

        } catch (JwtException e) {
            log.error("JWT parsing/validation failed", e);
            return CompletableFuture.failedFuture(new AuthenticationException("JWT parsing/validation failed", e));
        } catch (Exception e) {
            log.error("Unexpected error during JWT validation", e);
            return CompletableFuture.failedFuture(new AuthenticationException("Unexpected error during JWT validation", e));
        }
    }

    private String extractEmail(Claims claims) {
        String[] emailClaims = {"email", "preferred_username", "sub", "upn", "unique_name"};
        for (String claimName : emailClaims) {
            String value = claims.get(claimName, String.class);
            if (value != null && value.contains("@")) {
                return value;
            }
        }
        return null;
    }

    private OidcConfiguration matchOidcConfig(List<OidcConfiguration> configs, String issuer, String emailDomain) {
        if (issuer == null) {
            return null;
        }
        return configs.stream()
                      .filter(config -> issuer.equals(config.getAuthority()))
                      .filter(config -> emailDomain == null
                              || config.getDomains() == null
                              || config.getDomains().isEmpty()
                              || config.getDomains().contains(emailDomain))
                      .findFirst()
                      .orElse(null);
    }

    private List<String> extractRoles(OidcConfiguration config, Claims claims) {
        if (config.getRolesClaimPath() == null || config.getRolesClaimPath().isEmpty()) {
            return List.of();
        }
        Object rolesClaim = extractValueFromPath(claims, config.getRolesClaimPath());
        if (rolesClaim instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> roles = (List<String>) rolesClaim;
            return roles;
        }
        return List.of();
    }

    private Object extractValueFromPath(Claims claims, String path) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        String[] pathParts = path.split("\\.");
        Object currentValue = claims;
        for (String part : pathParts) {
            if (currentValue == null) {
                return null;
            }
            if (currentValue instanceof Claims c) {
                currentValue = c.get(part);
            } else if (currentValue instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) currentValue;
                currentValue = map.get(part);
            } else {
                return null;
            }
        }
        return currentValue;
    }

    /**
     * Looks up a pre-created IamUser by email within the target scope. This is the enforcement
     * point for scope isolation: even though the JWT has already been cryptographically verified,
     * access is denied unless an administrator has explicitly created a user record for this
     * email in this specific scope.
     * <p>
     * On first successful OIDC login, the user's {@code oidcSubject} and {@code oidcConfigId}
     * are populated from the JWT so the identity link is recorded for future reference.
     */
    private CompletableFuture<IamUser> lookupOrProvisionOidcUser(String oidcSubject,
                                                                 String email,
                                                                 OidcConfiguration config,
                                                                 String authScopeType,
                                                                 String authScopeId,
                                                                 Claims claims) {
        return userService.findByEmailAndScope(email, authScopeType, authScopeId)
                          .thenCompose(user -> {
                              if (user == null) {
                                  return CompletableFuture.<IamUser>failedFuture(
                                          new AuthenticationException("No user found for email " + email
                                                                              + " in scope " + authScopeType + "/" + authScopeId
                                                                              + ". User must be pre-created by an administrator."));
                              }
                              if (!user.isEnabled()) {
                                  return CompletableFuture.<IamUser>failedFuture(
                                          new AuthenticationException("User account is disabled"));
                              }
                              // If oidcSubject is not yet set, populate it on first OIDC login
                              if (user.getOidcSubject() == null) {
                                  user.setOidcSubject(oidcSubject);
                                  user.setOidcConfigId(config.getId());
                                  user.setAuthType(AuthType.OIDC);
                                  return userService.save(user);
                              }
                              return CompletableFuture.completedFuture(user);
                          });
    }

    private Participant createOidcParticipant(IamUser user, List<String> roles, Claims claims) {
        String name = claims.get("name", String.class);
        String preferredUsername = claims.get("preferred_username", String.class);

        Map<String, String> metadata = new HashMap<>(Map.of(
                ParticipantConstants.PARTICIPANT_TYPE_METADATA_KEY, ParticipantConstants.PARTICIPANT_TYPE_USER,
                "email", user.getEmail(),
                "displayName", name != null ? name : (preferredUsername != null ? preferredUsername : user.getEmail()),
                "authType", AuthType.OIDC.name(),
                "iss", claims.getIssuer(),
                "aud", claims.getAudience().stream().collect(Collectors.joining(", "))
        ));

        // See createParticipantFromUser: tenantId is not the auth scope id.
        return new DefaultParticipant(null, user.getId(),
                user.getAuthScopeType(), user.getAuthScopeId(), metadata, roles);
    }

}
