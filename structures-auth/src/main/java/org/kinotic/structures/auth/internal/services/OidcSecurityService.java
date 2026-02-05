
package org.kinotic.structures.auth.internal.services;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.lang.Collections;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.Jwk;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.kinotic.continuum.api.security.DefaultParticipant;
import org.kinotic.continuum.api.security.Participant;
import org.kinotic.continuum.api.security.ParticipantConstants;
import org.kinotic.continuum.api.security.SecurityService;
import org.kinotic.structures.auth.api.config.OidcSecurityServiceProperties;
import org.kinotic.structures.auth.api.domain.OidcProvider;
import org.kinotic.structures.auth.api.services.JwksService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.security.PublicKey;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;


@Slf4j
@AllArgsConstructor
@Component
@ConditionalOnProperty(prefix = "oidc-security-service", name = "enabled", havingValue = "true", matchIfMissing = false)
public class OidcSecurityService implements SecurityService {
    
    private final OidcSecurityServiceProperties properties;
    private final JwksService jwksService;

    @Override
    public CompletableFuture<Participant> authenticate(Map<String, String> authenticationInfo) {
        String authorizationHeader = getAuthorizationHeader(authenticationInfo);
        if (authorizationHeader == null) {
            return CompletableFuture.failedFuture(new RuntimeException("No authorization header found"));
        }

        String[] parts = authorizationHeader.split(" ");
        if (parts.length != 2 || !"Bearer".equalsIgnoreCase(parts[0])) {
            return CompletableFuture.failedFuture(new RuntimeException("Invalid authorization header format, expected 'Bearer <token>'"));
        }

        String token = parts[1];
        return verifyJwtToken(token);
    }

    private String getAuthorizationHeader(Map<String, String> authenticationInfo) {
        if (authenticationInfo.containsKey("authorization")) {
            return authenticationInfo.get("authorization");
        } else if (authenticationInfo.containsKey("Authorization")) {
            return authenticationInfo.get("Authorization");
        }
        return null;
    }

    private CompletableFuture<Participant> verifyJwtToken(String token) {
        return jwksService.getKeyFromToken(token)
            .thenCompose(key -> validateTokenWithKey(token, key));
    }

    private CompletableFuture<Participant> validateTokenWithKey(String token, Jwk<? extends Key> jwk) {
        try {
            // Extract the actual key from the Jwk
            Key key = jwk.toKey();
            
            // Verify it's a PublicKey for JWT verification
            if (!(key instanceof PublicKey)) {
                log.trace("Jwk does not contain a PublicKey instance");
                return CompletableFuture.failedFuture(new RuntimeException("Jwk does not contain a PublicKey instance"));
            }
            
            PublicKey publicKey = (PublicKey) key;
            
            // Parse and validate the JWT token
            Claims claims = Jwts.parser()
                    .verifyWith(publicKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            // Validate issuer - this must be hardcoded as email is a standard claim.
            // there are standard OIDC claims for email, preferred_username, and sub. We test for each
            // of those in that order.  Then try a few Microsoft specific claims.
            String[] emailClaims = new String[] { "email", "preferred_username", "sub", "upn", "unique_name" };
            String email = null;
            for(String emailClaim : emailClaims) {
                email = claims.get(emailClaim, String.class);
                if(email != null && email.contains("@")) {
                    break;
                }
            }
            if(email == null) {
                log.trace("Token has no email found in claims");
                return CompletableFuture.failedFuture(new RuntimeException("No email found in claims"));
            }

            String issuer = claims.getIssuer();
            log.trace("Token has issuer: {}", issuer);
            OidcProvider oidcProvider = isValidIssuer(issuer, email);
            if (oidcProvider == null) {
                log.trace("Token has invalid issuer: {}", issuer);
                return CompletableFuture.failedFuture(new RuntimeException("Invalid issuer: " + issuer));
            }
            
            // Validate audience
            Set<String> audiences = claims.getAudience();
            if (!isValidAudience(oidcProvider, audiences)) {
                log.trace("Token has invalid audience: {}", audiences);
                return CompletableFuture.failedFuture(new RuntimeException("Invalid audience: " + audiences));
            }

            // Validate expiration
            if (claims.getExpiration() != null && claims.getExpiration().before(java.util.Date.from(Instant.now()))) {
                log.trace("Token has expired");
                return CompletableFuture.failedFuture(new RuntimeException("Token has expired"));
            }

            // Extract roles from claims
            List<String> roles = null;
            if(oidcProvider.getRolesClaimPath() != null) {
                // function below will return an empty list if no roles are found at configured path
                roles = extractRolesFromClaims(oidcProvider, claims);
                if(roles.isEmpty()) {
                    log.warn("No roles found in claims, expected roles at claim path: {}", oidcProvider.getRolesClaimPath());
                    return CompletableFuture.failedFuture(new RuntimeException("No roles found in claims when one expected"));
                }

                if(oidcProvider.getRoles() != null && !oidcProvider.getRoles().isEmpty()) {
                    // we have roles and expected roles from the provider, so we need to validate the roles
                    if(!Collections.containsAny(roles, oidcProvider.getRoles())) {
                        log.warn("User roles {} do not match any required roles: {}", roles, oidcProvider.getRoles());
                        return CompletableFuture.failedFuture(new RuntimeException("User does not have any required roles. Found: " + roles + ", Required: " + oidcProvider.getRoles()));
                    }
                }
            }

            // Create participant from claims
            Participant participant = createParticipantFromClaims(oidcProvider, claims, roles);
            return CompletableFuture.completedFuture(participant);

        } catch (JwtException e) {
            log.error("JWT parsing/validation failed", e);
            return CompletableFuture.failedFuture(new RuntimeException("JWT parsing/validation failed", e));
        } catch (Exception e) {
            log.error("Unexpected error during JWT validation", e);
            return CompletableFuture.failedFuture(new RuntimeException("Unexpected error during JWT validation", e));
        }
    }

    private OidcProvider isValidIssuer(String issuer, String email) {
        if (issuer == null) {
            return null;
        }

        OidcProvider oidcProvider = properties.getOidcProviders().stream()
                .filter(provider -> issuer.equals(provider.getAuthority()) && provider.getDomains().contains(email.split("@")[1]))
                .findFirst()
                .orElse(null);

        if (oidcProvider == null) {
            log.warn("No allowed Oidc Providers configured for issuer: {}", issuer);
            return null;
        }

        if(!oidcProvider.isEnabled()) {
            log.warn("Oidc Provider found but is not enabled: {}", issuer);
            return null;
        }

        return oidcProvider;
    }

    private boolean isValidAudience(OidcProvider oidcProvider, Set<String> audiences) {

        if (oidcProvider.getAudience() == null || oidcProvider.getAudience().isEmpty()) {
            log.warn("No allowed audience configured for issuer: {}", oidcProvider.getAuthority());
            return false;
        }

        boolean isValid = false;
        for(String audience : audiences) {
            if(oidcProvider.getAudience().equals(audience)) {
                isValid = true;
                break;
            }
        }

        if(!isValid) {
            log.warn("Audience is not valid: {}", audiences);
        }

        return isValid;
    }

    private Participant createParticipantFromClaims(OidcProvider oidcProvider, Claims claims, List<String> roles) {
        // Extract user information from claims
        String subject = claims.getSubject();
        String email = claims.get("email", String.class);
        String name = claims.get("name", String.class);
        String preferredUsername = claims.get("preferred_username", String.class);
        
        // Extract tenant ID from claims or use a default
        String tenantId = extractValueFromPath(claims, properties.getTenantIdFieldName(), String.class);
        
        if (tenantId == null) {
            tenantId = "default"; // Default tenant if not specified
        }


        // TODO: for social logins, we don't get any roles.. that would need to be tracked in app metadata.  This 
        //   is where we need to store authenticated users and their roles.  The first user is automatically an admin
        //   and can add other users as admins/users.  If that is how we manage that, how do we handle the enterprise
        //   cases, automatically configure those users as we validate the tokens? 

        // Create metadata
        HashMap<String, String> metadata = new HashMap<>(Map.of(
            ParticipantConstants.PARTICIPANT_TYPE_METADATA_KEY,
            ParticipantConstants.PARTICIPANT_TYPE_USER,
            "email", email != null ? email : "",
            "name", name != null ? name : (preferredUsername != null ? preferredUsername : subject),
            "iss", claims.getIssuer(),
            "aud", claims.getAudience().stream().collect(Collectors.joining(", "))
        ));

        if(oidcProvider.getMetadata() != null && !oidcProvider.getMetadata().isEmpty()) {
            metadata.putAll(oidcProvider.getMetadata());
        }

        return new DefaultParticipant(tenantId, subject, metadata, roles);
    }

    /*
     * Extract roles from claims using the roles claim path.
     * If the roles claim path is not provided, we do not extract roles from the JWT token.
     * If the roles claim path is nested, the path is a dot-separated string of the nested properties.
     * Example: "realm_access.roles" or "groups"
     */
    private List<String> extractRolesFromClaims(OidcProvider oidcProvider, Claims claims) {
        if (oidcProvider.getRolesClaimPath() == null || oidcProvider.getRolesClaimPath().isEmpty()) {
            return List.of();
        }

        Object rolesClaim = extractValueFromPath(claims, oidcProvider.getRolesClaimPath());

        if (rolesClaim != null && rolesClaim instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> roles = (List<String>) rolesClaim;
            return roles;
        }

        // Default to empty list if no roles found
        return List.of();
    }

    /**
     * Extracts a value from Claims using a dot-separated JSON path.
     * Supports nested paths like "realm_access.roles" or "groups".
     * 
     * @param claims The JWT claims to extract from
     * @param path The dot-separated path to the desired value
     * @return The value at the specified path, or null if not found
     */
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

            if (currentValue instanceof Claims) {
                // Extract from Claims using the part as a key
                currentValue = ((Claims) currentValue).get(part);
            } else if (currentValue instanceof Map) {
                // Extract from Map using the part as a key
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) currentValue;
                currentValue = map.get(part);
            } else {
                // If we encounter a non-Map/Claims object in the middle of the path, return null
                return null;
            }
        }

        return currentValue;
    }

    /**
     * Public method to extract values from Claims using JSON paths.
     * This can be used by other services that need to extract nested claim values.
     * 
     * @param claims The JWT claims to extract from
     * @param path The dot-separated path to the desired value
     * @param targetType The expected type of the value
     * @param <T> The type parameter
     * @return The value at the specified path cast to the target type, or null if not found or wrong type
     */
    public <T> T extractValueFromPath(Claims claims, String path, Class<T> targetType) {
        Object value = extractValueFromPath(claims, path);
        
        if (value != null && targetType.isInstance(value)) {
            return targetType.cast(value);
        }
        
        return null;
    }
}
