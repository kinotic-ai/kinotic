package org.kinotic.domain.internal.api.services.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.kinotic.domain.api.model.security.CodeExchangeResult;
import org.kinotic.domain.api.model.security.UserParticipantIdentity;
import org.kinotic.domain.api.model.security.PendingOAuthAuthorization;
import org.kinotic.domain.api.services.security.OAuthAuthorizationService;
import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.domain.internal.api.model.OAuthAuthorizationGrant;
import org.kinotic.domain.internal.api.repositories.ParticipantIdentityRepository;
import org.kinotic.domain.internal.api.repositories.OAuthAuthorizationGrantRepository;
import org.kinotic.domain.internal.api.rest.support.OAuth2Util;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultOAuthAuthorizationService implements OAuthAuthorizationService {

    /** How long an authorization request may await consent before it expires. */
    private static final long REQUEST_TTL_MS = 10 * 60 * 1000L;

    /** How long a minted authorization code remains exchangeable. */
    private static final long CODE_TTL_MS = 60 * 1000L;

    /** Bytes of entropy for authorization codes. */
    private static final int TOKEN_BYTES = 32;

    private final ClientMetadataDocumentService clientMetadataDocumentService;
    private final OAuthAuthorizationGrantRepository grantRepository;
    private final ParticipantIdentityRepository identityRepository;

    @Override
    public CompletableFuture<String> createAuthorizationRequest(String clientId,
                                                                String redirectUri,
                                                                String codeChallenge,
                                                                String scope,
                                                                String resource,
                                                                String state) {
        Validate.notBlank(clientId, "client_id is required");
        Validate.notBlank(redirectUri, "redirect_uri is required");
        Validate.notBlank(codeChallenge, "code_challenge is required");
        return clientMetadataDocumentService.resolve(clientId)
                .thenCompose(client -> {
                    if (!matchesRegisteredRedirectUri(client.getRedirectUris(), redirectUri)) {
                        return CompletableFuture.failedFuture(
                                new IllegalArgumentException("redirect_uri is not registered for this client"));
                    }
                    Date now = new Date();
                    OAuthAuthorizationGrant grant = new OAuthAuthorizationGrant()
                            .setId(UUID.randomUUID().toString())
                            .setClientId(clientId)
                            .setClientName(client.getClientName())
                            .setRedirectUri(redirectUri)
                            .setCodeChallenge(codeChallenge)
                            .setScope(scope)
                            .setResource(resource)
                            .setState(state)
                            .setCreated(now)
                            .setExpiresAt(new Date(now.getTime() + REQUEST_TTL_MS));
                    return grantRepository.saveSync(grant)
                                          .toCompletionStage().toCompletableFuture()
                                          .thenApply(OAuthAuthorizationGrant::getId);
                });
    }

    @Override
    public CompletableFuture<PendingOAuthAuthorization> findPending(String requestId) {
        return loadPendingGrant(requestId)
                .thenApply(grant -> new PendingOAuthAuthorization(grant.getClientName(),
                                                                  grant.getClientId(),
                                                                  grant.getScope()));
    }

    @Override
    public CompletableFuture<String> approve(String requestId, String identityId) {
        Validate.notBlank(identityId, "identityId is required");
        return loadPendingGrant(requestId)
                .thenCompose(grant -> {
                    String code = DomainUtil.generateUrlSafeToken(TOKEN_BYTES);
                    grant.setIdentityId(identityId)
                         .setCodeHash(DomainUtil.sha256Hex(code))
                         // the code's own, shorter expiry replaces the consent window
                         .setExpiresAt(new Date(System.currentTimeMillis() + CODE_TTL_MS));
                    return grantRepository.saveSync(grant)
                                          .toCompletionStage().toCompletableFuture()
                                          .thenApply(saved -> redirectUrl(grant, "code", code));
                });
    }

    @Override
    public CompletableFuture<String> deny(String requestId) {
        return loadPendingGrant(requestId)
                .thenCompose(grant -> grantRepository.deleteById(grant.getId())
                                                     .toCompletionStage().toCompletableFuture()
                                                     .thenApply(v -> redirectUrl(grant, "error", "access_denied")));
    }

    @Override
    public CompletableFuture<CodeExchangeResult> exchangeCode(String code,
                                                   String clientId,
                                                   String redirectUri,
                                                   String codeVerifier) {
        Validate.notBlank(code, "code is required");
        Validate.notBlank(clientId, "client_id is required");
        Validate.notBlank(redirectUri, "redirect_uri is required");
        Validate.notBlank(codeVerifier, "code_verifier is required");
        return grantRepository.findByCodeHash(DomainUtil.sha256Hex(code))
                .toCompletionStage().toCompletableFuture()
                .thenCompose(grant -> {
                    if (grant == null) {
                        return CompletableFuture.failedFuture(new IllegalArgumentException("Unknown authorization code"));
                    }
                    // consume the grant before judging it, so a failed exchange burns the code too.
                    // the delete must wait for the index refresh: findByCodeHash is a search
                    return grantRepository.deleteByIdSync(grant.getId())
                                          .toCompletionStage().toCompletableFuture()
                                          .thenCompose(v -> {
                        if (grant.getExpiresAt().before(new Date())) {
                            return CompletableFuture.failedFuture(new IllegalArgumentException("Authorization code has expired"));
                        }
                        if (!grant.getClientId().equals(clientId) || !grant.getRedirectUri().equals(redirectUri)) {
                            return CompletableFuture.failedFuture(
                                    new IllegalArgumentException("Authorization code was not issued to this client"));
                        }
                        if (!OAuth2Util.s256Challenge(codeVerifier).equals(grant.getCodeChallenge())) {
                            return CompletableFuture.failedFuture(new IllegalArgumentException("PKCE verification failed"));
                        }
                        return loadEnabledUser(grant.getIdentityId())
                                .thenApply(approver -> new CodeExchangeResult(approver,
                                                                              grant.getClientId(),
                                                                              grant.getClientName()));
                    });
                });
    }

    /**
     * Whether {@code requested} is one of the client's registered redirect URIs. Exact match, with
     * the one exception RFC 8252 Section 7.3 requires: a loopback URI matches regardless of port,
     * because a native client binds its callback to an ephemeral port chosen at runtime and cannot
     * register it ahead of time. Claude Code registers {@code http://localhost/callback} and calls
     * back on whatever port it obtained.
     * <p>
     * Everything else still has to match exactly — a partial or prefix match would let a look-alike
     * URI capture codes — and the grant stores the requested URI, so the code exchange compares
     * against the port the flow actually began with.
     */
    private static boolean matchesRegisteredRedirectUri(List<String> registered, String requested) {
        boolean ret = registered.contains(requested);
        if (!ret) {
            URI requestedUri = toUri(requested);
            if (requestedUri != null && OAuth2Util.isLoopbackHost(requestedUri.getHost())) {
                for (String candidate : registered) {
                    URI candidateUri = toUri(candidate);
                    if (candidateUri != null
                            && Objects.equals(requestedUri.getScheme(), candidateUri.getScheme())
                            && Objects.equals(requestedUri.getHost(), candidateUri.getHost())
                            && Objects.equals(requestedUri.getPath(), candidateUri.getPath())
                            && Objects.equals(requestedUri.getQuery(), candidateUri.getQuery())) {
                        ret = true;
                        break;
                    }
                }
            }
        }
        return ret;
    }

    private static URI toUri(String value) {
        URI ret;
        try {
            ret = new URI(value);
        } catch (Exception e) {
            ret = null;
        }
        return ret;
    }

    private CompletableFuture<OAuthAuthorizationGrant> loadPendingGrant(String requestId) {
        Validate.notBlank(requestId, "requestId is required");
        return grantRepository.findById(requestId)
                .toCompletionStage().toCompletableFuture()
                .thenCompose(grant -> {
                    if (grant == null) {
                        return CompletableFuture.failedFuture(new IllegalArgumentException("Unknown authorization request"));
                    }
                    if (grant.getExpiresAt().before(new Date())) {
                        return grantRepository.deleteById(grant.getId())
                                .toCompletionStage().toCompletableFuture()
                                .thenCompose(v -> CompletableFuture.failedFuture(
                                        new IllegalArgumentException("Authorization request has expired")));
                    }
                    if (grant.getIdentityId() != null) {
                        return CompletableFuture.failedFuture(
                                new IllegalArgumentException("Authorization request has already been decided"));
                    }
                    return CompletableFuture.completedFuture(grant);
                });
    }

    private CompletableFuture<UserParticipantIdentity> loadEnabledUser(String identityId) {
        return identityRepository.findById(identityId)
                .toCompletionStage().toCompletableFuture()
                .thenApply(UserParticipantIdentity.class::cast)
                .thenCompose(user -> (user == null || !user.isEnabled())
                        ? CompletableFuture.failedFuture(new IllegalArgumentException("User is not available"))
                        : CompletableFuture.completedFuture(user));
    }

    private static String redirectUrl(OAuthAuthorizationGrant grant, String parameter, String value) {
        StringBuilder url = new StringBuilder(grant.getRedirectUri());
        url.append(grant.getRedirectUri().contains("?") ? '&' : '?')
           .append(parameter).append('=').append(URLEncoder.encode(value, StandardCharsets.UTF_8));
        if (grant.getState() != null) {
            url.append("&state=").append(URLEncoder.encode(grant.getState(), StandardCharsets.UTF_8));
        }
        return url.toString();
    }

}
