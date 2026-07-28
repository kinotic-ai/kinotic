package org.kinotic.domain.internal.api.services.iam;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.kinotic.domain.api.model.iam.IamUser;
import org.kinotic.domain.api.model.iam.PendingOAuthAuthorization;
import org.kinotic.domain.api.model.iam.RegisteredOAuthClient;
import org.kinotic.domain.api.services.iam.OAuthAuthorizationService;
import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.domain.internal.api.model.OAuthAuthorizationGrant;
import org.kinotic.domain.internal.api.model.OAuthClient;
import org.kinotic.domain.internal.api.repositories.IamUserRepository;
import org.kinotic.domain.internal.api.repositories.OAuthAuthorizationGrantRepository;
import org.kinotic.domain.internal.api.repositories.OAuthClientRepository;
import org.kinotic.domain.internal.api.rest.support.OAuth2Util;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
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

    /** Bytes of entropy for client ids and authorization codes. */
    private static final int TOKEN_BYTES = 32;

    private final OAuthClientRepository clientRepository;
    private final OAuthAuthorizationGrantRepository grantRepository;
    private final IamUserRepository iamUserRepository;

    @Override
    public CompletableFuture<RegisteredOAuthClient> registerClient(String clientName, List<String> redirectUris) {
        Validate.notEmpty(redirectUris, "redirect_uris is required");
        for (String redirectUri : redirectUris) {
            validateRedirectUri(redirectUri);
        }
        Date now = new Date();
        OAuthClient client = new OAuthClient()
                .setId(DomainUtil.generateUrlSafeToken(TOKEN_BYTES))
                .setClientName(clientName == null || clientName.isBlank() ? "MCP Client" : clientName.trim())
                .setRedirectUris(List.copyOf(redirectUris))
                .setCreated(now);
        return clientRepository.saveSync(client)
                               .thenApply(saved -> new RegisteredOAuthClient(saved.getId(),
                                                                             saved.getClientName(),
                                                                             saved.getRedirectUris(),
                                                                             now.getTime() / 1000L));
    }

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
        return clientRepository.findById(clientId)
                .thenCompose(client -> {
                    if (client == null) {
                        return CompletableFuture.failedFuture(new IllegalArgumentException("Unknown client_id"));
                    }
                    // exact match only: a partial or prefix match would let a look-alike URI capture codes
                    if (client.getRedirectUris() == null || !client.getRedirectUris().contains(redirectUri)) {
                        return CompletableFuture.failedFuture(
                                new IllegalArgumentException("redirect_uri is not registered for this client"));
                    }
                    Date now = new Date();
                    OAuthAuthorizationGrant grant = new OAuthAuthorizationGrant()
                            .setId(UUID.randomUUID().toString())
                            .setClientId(clientId)
                            .setRedirectUri(redirectUri)
                            .setCodeChallenge(codeChallenge)
                            .setScope(scope)
                            .setResource(resource)
                            .setState(state)
                            .setCreated(now)
                            .setExpiresAt(new Date(now.getTime() + REQUEST_TTL_MS));
                    return grantRepository.saveSync(grant).thenApply(OAuthAuthorizationGrant::getId);
                });
    }

    @Override
    public CompletableFuture<PendingOAuthAuthorization> findPending(String requestId) {
        return loadPendingGrant(requestId)
                .thenCompose(grant -> clientRepository.findById(grant.getClientId())
                        .thenApply(client -> new PendingOAuthAuthorization(
                                client == null ? grant.getClientId() : client.getClientName(),
                                grant.getScope())));
    }

    @Override
    public CompletableFuture<String> approve(String requestId, String userId) {
        Validate.notBlank(userId, "userId is required");
        return loadPendingGrant(requestId)
                .thenCompose(grant -> {
                    String code = DomainUtil.generateUrlSafeToken(TOKEN_BYTES);
                    grant.setUserId(userId)
                         .setCodeHash(DomainUtil.sha256Hex(code))
                         // the code's own, shorter expiry replaces the consent window
                         .setExpiresAt(new Date(System.currentTimeMillis() + CODE_TTL_MS));
                    return grantRepository.saveSync(grant)
                                          .thenApply(saved -> redirectUrl(grant, "code", code));
                });
    }

    @Override
    public CompletableFuture<String> deny(String requestId) {
        return loadPendingGrant(requestId)
                .thenCompose(grant -> grantRepository.deleteById(grant.getId())
                                                     .thenApply(v -> redirectUrl(grant, "error", "access_denied")));
    }

    @Override
    public CompletableFuture<IamUser> exchangeCode(String code,
                                                   String clientId,
                                                   String redirectUri,
                                                   String codeVerifier) {
        Validate.notBlank(code, "code is required");
        Validate.notBlank(clientId, "client_id is required");
        Validate.notBlank(redirectUri, "redirect_uri is required");
        Validate.notBlank(codeVerifier, "code_verifier is required");
        return grantRepository.findByCodeHash(DomainUtil.sha256Hex(code))
                .thenCompose(grant -> {
                    if (grant == null) {
                        return CompletableFuture.failedFuture(new IllegalArgumentException("Unknown authorization code"));
                    }
                    // consume the grant before judging it, so a failed exchange burns the code too.
                    // the delete must wait for the index refresh: findByCodeHash is a search, so an
                    // unrefreshed delete leaves the code exchangeable until the next refresh interval
                    return grantRepository.deleteByIdSync(grant.getId()).thenCompose(v -> {
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
                        return loadEnabledUser(grant.getUserId());
                    });
                });
    }

    private CompletableFuture<OAuthAuthorizationGrant> loadPendingGrant(String requestId) {
        Validate.notBlank(requestId, "requestId is required");
        return grantRepository.findById(requestId)
                .thenCompose(grant -> {
                    if (grant == null) {
                        return CompletableFuture.failedFuture(new IllegalArgumentException("Unknown authorization request"));
                    }
                    if (grant.getExpiresAt().before(new Date())) {
                        return grantRepository.deleteById(grant.getId())
                                .thenCompose(v -> CompletableFuture.failedFuture(
                                        new IllegalArgumentException("Authorization request has expired")));
                    }
                    if (grant.getUserId() != null) {
                        return CompletableFuture.failedFuture(
                                new IllegalArgumentException("Authorization request has already been decided"));
                    }
                    return CompletableFuture.completedFuture(grant);
                });
    }

    private CompletableFuture<IamUser> loadEnabledUser(String userId) {
        return iamUserRepository.findById(userId)
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

    /**
     * A redirect URI must be absolute with an https scheme; plain http is allowed only for
     * loopback hosts (RFC 8252 native clients such as Claude Code's localhost callback).
     */
    private static void validateRedirectUri(String redirectUri) {
        URI uri;
        try {
            uri = URI.create(redirectUri);
        } catch (Exception e) {
            throw new IllegalArgumentException("redirect_uri is not a valid URI: " + redirectUri);
        }
        String scheme = uri.getScheme();
        String host = uri.getHost();
        boolean loopback = "localhost".equals(host) || "127.0.0.1".equals(host) || "[::1]".equals(host);
        if (!"https".equals(scheme) && !("http".equals(scheme) && loopback)) {
            throw new IllegalArgumentException("redirect_uri must be https, or http on a loopback host: " + redirectUri);
        }
        if (uri.getFragment() != null) {
            throw new IllegalArgumentException("redirect_uri must not contain a fragment: " + redirectUri);
        }
    }
}
