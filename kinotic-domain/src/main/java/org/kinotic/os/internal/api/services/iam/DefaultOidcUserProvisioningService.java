package org.kinotic.os.internal.api.services.iam;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.os.api.model.iam.AuthType;
import org.kinotic.os.api.model.iam.IamUser;
import org.kinotic.os.api.model.iam.OidcConfiguration;
import org.kinotic.os.api.model.iam.PendingRegistration;
import org.kinotic.os.api.services.iam.IamUserService;
import org.kinotic.os.api.services.iam.OidcUserProvisioningService;
import org.kinotic.os.api.services.iam.PendingRegistrationService;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Implements the three provisioning modes for the OIDC callback:
 * <ul>
 *   <li><b>AUTO</b> — create {@link IamUser} directly from verified id_token claims.</li>
 *   <li><b>REGISTRATION_REQUIRED</b> — stash identity in a {@link PendingRegistration} and
 *       return a token for the completion form.</li>
 *   <li><b>INVITE_ONLY</b> — not yet implemented; rejects with a clear error so the enum
 *       value remains a valid persisted state but callers know the feature isn't wired.</li>
 * </ul>
 * Always rejects id_tokens with {@code email_verified=false} — we never provision from
 * self-asserted emails.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultOidcUserProvisioningService implements OidcUserProvisioningService {

    private final IamUserService iamUserService;
    private final PendingRegistrationService pendingRegistrationService;

    @Override
    public CompletableFuture<Result> resolve(OidcConfiguration config,
                                             Map<String, Object> claims,
                                             String authScopeType,
                                             String authScopeId) {
        String sub = stringClaim(claims, "sub");
        String email = stringClaim(claims, "email");
        Boolean emailVerified = booleanClaim(claims, "email_verified");
        String displayName = firstPresent(claims, "name", "preferred_username", "email");

        if (sub == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException(
                    "OIDC id_token missing 'sub' claim"));
        }
        if (email == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException(
                    "OIDC id_token missing 'email' claim"));
        }
        if (!Boolean.TRUE.equals(emailVerified)) {
            return CompletableFuture.failedFuture(new SecurityException(
                    "OIDC id_token has email_verified=false; refusing to provision"));
        }

        return iamUserService.findByOidcIdentityAndScope(sub, config.getId(), authScopeType, authScopeId)
                             .thenCompose(existing -> {
            if (existing != null) {
                return CompletableFuture.completedFuture((Result) new Result.Authenticated(existing));
            }
            return switch (config.getProvisioningMode()) {
                case AUTO -> autoProvision(config, sub, email, displayName, authScopeType, authScopeId);
                case REGISTRATION_REQUIRED -> pendingProvision(config, sub, email, displayName, claims,
                                                                authScopeType, authScopeId);
                case INVITE_ONLY -> CompletableFuture.failedFuture(new UnsupportedOperationException(
                        "INVITE_ONLY provisioning mode is not yet wired. Use AUTO or REGISTRATION_REQUIRED."));
            };
        });
    }

    private CompletableFuture<Result> autoProvision(OidcConfiguration config,
                                                    String sub,
                                                    String email,
                                                    String displayName,
                                                    String authScopeType,
                                                    String authScopeId) {
        Date now = new Date();
        IamUser user = new IamUser()
                .setId(UUID.randomUUID().toString())
                .setEmail(email)
                .setDisplayName(displayName)
                .setAuthType(AuthType.OIDC)
                .setOidcSubject(sub)
                .setOidcConfigId(config.getId())
                .setAuthScopeType(authScopeType)
                .setAuthScopeId(authScopeId)
                .setEnabled(true)
                .setDefault(true)
                .setCreated(now)
                .setUpdated(now);
        log.info("Auto-provisioning IamUser for OIDC identity sub={} config={} scope={}/{}",
                 sub, config.getId(), authScopeType, authScopeId);
        return iamUserService.save(user).thenApply(Result.Authenticated::new);
    }

    private CompletableFuture<Result> pendingProvision(OidcConfiguration config,
                                                       String sub,
                                                       String email,
                                                       String displayName,
                                                       Map<String, Object> claims,
                                                       String authScopeType,
                                                       String authScopeId) {
        PendingRegistration pending = new PendingRegistration()
                .setOidcSubject(sub)
                .setOidcConfigId(config.getId())
                .setEmail(email)
                .setDisplayName(displayName)
                .setAuthScopeType(authScopeType)
                .setAuthScopeId(authScopeId)
                .setAdditionalClaims(claims);
        log.info("Creating PendingRegistration for OIDC identity sub={} config={} scope={}/{}",
                 sub, config.getId(), authScopeType, authScopeId);
        return pendingRegistrationService.create(pending).thenApply(Result.NeedsRegistration::new);
    }

    private static String stringClaim(Map<String, Object> claims, String name) {
        Object value = claims.get(name);
        return value == null ? null : value.toString();
    }

    private static Boolean booleanClaim(Map<String, Object> claims, String name) {
        Object value = claims.get(name);
        if (value instanceof Boolean b) return b;
        if (value instanceof String s) return Boolean.valueOf(s);
        return null;
    }

    private static String firstPresent(Map<String, Object> claims, String... names) {
        for (String name : names) {
            String value = stringClaim(claims, name);
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }
}
