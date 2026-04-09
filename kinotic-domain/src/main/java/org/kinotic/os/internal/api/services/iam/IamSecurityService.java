package org.kinotic.os.internal.api.services.iam;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.lang.Collections;
import io.jsonwebtoken.security.Jwk;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.exceptions.AuthenticationException;
import org.kinotic.core.api.security.DefaultParticipant;
import org.kinotic.core.api.security.Participant;
import org.kinotic.core.api.security.ParticipantConstants;
import org.kinotic.core.api.security.SecurityService;
import org.kinotic.core.internal.api.security.JwksService;
import org.kinotic.os.api.model.iam.AuthScope;
import org.kinotic.os.api.model.iam.AuthType;
import org.kinotic.os.api.model.iam.IamUser;
import org.kinotic.os.api.model.iam.OidcConfiguration;
import org.kinotic.os.internal.model.iam.IamCredential;
import org.kinotic.os.internal.services.iam.IamCredentialStore;
import org.kinotic.os.internal.services.iam.OidcConfigLookup;
import org.kinotic.os.internal.services.iam.PasswordService;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.security.PublicKey;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class IamSecurityService implements SecurityService {

    private final DefaultIamUserService userService;
    private final IamCredentialStore credentialStore;
    private final OidcConfigLookup oidcConfigLookup;
    private final JwksService jwksService;
    private final PasswordService passwordService;

    @Override
    public CompletableFuture<Participant> authenticate(Map<String, String> authenticationInfo) {
        String authScopeTypeHeader = authenticationInfo.get("authScopeType");
        String authScopeId = authenticationInfo.get("authScopeId");

        if (authScopeTypeHeader == null) {
            return CompletableFuture.failedFuture(new AuthenticationException("authScopeType header is required"));
        }

        AuthScope authScopeType;
        try {
            authScopeType = AuthScope.valueOf(authScopeTypeHeader);
        } catch (IllegalArgumentException e) {
            return CompletableFuture.failedFuture(new AuthenticationException("Invalid authScopeType: " + authScopeTypeHeader));
        }

        String authHeader = getAuthorizationHeader(authenticationInfo);

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authenticateOidc(authScopeType, authScopeId, authHeader);
        } else {
            return authenticateEmailPassword(authScopeType, authScopeId, authenticationInfo);
        }
    }

    private String getAuthorizationHeader(Map<String, String> authenticationInfo) {
        if (authenticationInfo.containsKey("authorization")) {
            return authenticationInfo.get("authorization");
        } else if (authenticationInfo.containsKey("Authorization")) {
            return authenticationInfo.get("Authorization");
        }
        return null;
    }

    // ---- Email/Password Authentication ----

    private CompletableFuture<Participant> authenticateEmailPassword(AuthScope authScopeType,
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
                    return credentialStore.findById(user.getId())
                            .thenCompose(credential -> verifyPasswordAndCreateParticipant(user, credential, password));
                });
    }

    private CompletableFuture<Participant> verifyPasswordAndCreateParticipant(IamUser user,
                                                                              IamCredential credential,
                                                                              String password) {
        if (credential == null) {
            return CompletableFuture.failedFuture(new AuthenticationException("Invalid credentials"));
        }
        if (!passwordService.verify(password, credential.getPasswordHash())) {
            return CompletableFuture.failedFuture(new AuthenticationException("Invalid credentials"));
        }
        return CompletableFuture.completedFuture(createParticipantFromUser(user));
    }

    private Participant createParticipantFromUser(IamUser user) {
        String tenantId = user.getAuthScopeId() != null ? user.getAuthScopeId() : "system";

        Map<String, String> metadata = new HashMap<>(Map.of(
                ParticipantConstants.PARTICIPANT_TYPE_METADATA_KEY, ParticipantConstants.PARTICIPANT_TYPE_USER,
                "email", user.getEmail(),
                "displayName", user.getDisplayName() != null ? user.getDisplayName() : user.getEmail(),
                "authScopeType", user.getAuthScopeType().name(),
                "authType", user.getAuthType().name()
        ));

        if (user.getAuthScopeId() != null) {
            metadata.put("authScopeId", user.getAuthScopeId());
        }

        return new DefaultParticipant(tenantId, user.getId(), metadata, List.of());
    }

    // ---- OIDC Authentication ----

    private CompletableFuture<Participant> authenticateOidc(AuthScope authScopeType,
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

    private CompletableFuture<Participant> validateOidcToken(String token,
                                                              Jwk<? extends Key> jwk,
                                                              List<OidcConfiguration> configs,
                                                              AuthScope authScopeType,
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

    private CompletableFuture<IamUser> lookupOrProvisionOidcUser(String oidcSubject,
                                                                  String email,
                                                                  OidcConfiguration config,
                                                                  AuthScope authScopeType,
                                                                  String authScopeId,
                                                                  Claims claims) {
        // First try to find by email + scope (admin may have pre-created the user)
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
        String tenantId = user.getAuthScopeId() != null ? user.getAuthScopeId() : "system";
        String name = claims.get("name", String.class);
        String preferredUsername = claims.get("preferred_username", String.class);

        Map<String, String> metadata = new HashMap<>(Map.of(
                ParticipantConstants.PARTICIPANT_TYPE_METADATA_KEY, ParticipantConstants.PARTICIPANT_TYPE_USER,
                "email", user.getEmail(),
                "displayName", name != null ? name : (preferredUsername != null ? preferredUsername : user.getEmail()),
                "authScopeType", user.getAuthScopeType().name(),
                "authType", AuthType.OIDC.name(),
                "iss", claims.getIssuer(),
                "aud", claims.getAudience().stream().collect(Collectors.joining(", "))
        ));

        if (user.getAuthScopeId() != null) {
            metadata.put("authScopeId", user.getAuthScopeId());
        }

        return new DefaultParticipant(tenantId, user.getId(), metadata, roles);
    }

}
