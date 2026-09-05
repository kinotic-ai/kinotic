package org.kinotic.domain.internal.api.services.security;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.domain.api.config.KinoticDomainProperties;
import org.kinotic.domain.api.config.OAuthProperties;
import org.kinotic.domain.internal.api.model.ClientMetadataDocument;
import org.kinotic.domain.internal.api.rest.support.OAuth2Util;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.net.InetAddress;
import java.net.URI;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves the {@code client_id} of an authorization request into the client's metadata by
 * fetching the client metadata document served at the Client ID Metadata Document URL it names
 * (draft-ietf-oauth-client-id-metadata-document). That URL is the client's identity: it had to
 * control the host to serve a document there, so the name the consent page shows is attributable
 * to a domain rather than self-asserted.
 * <p>
 * Resolution fails — and with it the authorization request — unless the URL is a well-formed
 * client identifier, its host resolves to a routable address, the fetch answers {@code 200} within
 * the size limit, and the document names itself and registers at least one redirect URI.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClientMetadataDocumentService {

    private final KinoticDomainProperties domainProperties;
    private final JsonMapper jsonMapper;
    private final Vertx vertx;

    private WebClient webClient;
    private final Map<String, CachedClient> cache = new ConcurrentHashMap<>();

    @PostConstruct
    public void start() {
        // redirects are followed manually nowhere: Section 4 forbids following them automatically,
        // since a redirect would let the document be served from a host other than the client_id's
        webClient = WebClient.create(vertx, new WebClientOptions().setFollowRedirects(false));
    }

    /**
     * Resolves {@code clientId} into the client metadata document it names, fetching and validating it
     * unless a still-valid cached copy is held. The returned document has passed every rule in
     * Section 4.1, so its {@code redirectUris} are non-empty and usable and its {@code clientName}
     * is set to something displayable.
     *
     * @param clientId the {@code client_id} from the authorization request
     * @return a {@link Future} emitting the validated document, failing when the
     *         client_id is not permitted or the document is unreachable or invalid
     */
    public Future<ClientMetadataDocument> resolve(String clientId) {
        Future<ClientMetadataDocument> ret;
        try {
            URI uri = validateClientIdUrl(clientId);
            CachedClient cached = cache.get(clientId);
            if (cached != null && cached.expiresAt().after(new Date())) {
                ret = Future.succeededFuture(cached.document());
            } else {
                ret = requireRoutableHost(uri.getHost())
                        .compose(v -> fetchMetadata(clientId))
                        .onSuccess(document -> cache.put(clientId,
                                                         new CachedClient(document, cacheExpiry())));
            }
        } catch (IllegalArgumentException e) {
            ret = Future.failedFuture(e);
        }
        return ret;
    }

    /**
     * Client identifier rules from Section 3, plus the deployment's allowlist. Section 6.10 makes
     * pre-registering document URLs an explicit deployment choice for servers that do not want to
     * accept unknown clients.
     */
    private URI validateClientIdUrl(String clientId) {
        OAuthProperties oauth = domainProperties.getDomain().getOauth();

        // client_id comes straight off the query string and may be absent
        if (clientId == null || !oauth.getAllowedClientIds().contains(clientId)) {
            throw new IllegalArgumentException("client_id is not allowed by this deployment");
        }

        URI uri;
        try {
            uri = new URI(clientId);
        } catch (Exception e) {
            throw new IllegalArgumentException("client_id is not a valid URL: " + clientId);
        }

        String path = uri.getPath();

        if (!"https".equals(uri.getScheme())) {
            throw new IllegalArgumentException("client_id must be an https URL");
        }

        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new IllegalArgumentException("client_id must contain a host");
        }

        if (path == null || path.isBlank() || "/".equals(path)) {
            throw new IllegalArgumentException("client_id must contain a path component");
        }

        if (path.contains("/.") || path.contains("/..")) {
            throw new IllegalArgumentException("client_id must not contain dot path segments");
        }

        if (uri.getFragment() != null) {
            throw new IllegalArgumentException("client_id must not contain a fragment");
        }

        if (uri.getUserInfo() != null) {
            throw new IllegalArgumentException("client_id must not contain a username or password");
        }

        return uri;
    }

    /**
     * Section 6.5 — the document URL must not resolve to a special-use address, or the client could
     * steer the fetch at hosts only this server can reach.
     */
    private Future<Void> requireRoutableHost(String host) {
        // getAllByName blocks on DNS; the resolution is advisory because the connect re-resolves,
        // so a rebinding attacker can still move the address between this check and the fetch
        return vertx.executeBlocking(() -> {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (address.isLoopbackAddress() || address.isAnyLocalAddress()
                        || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                        || address.isMulticastAddress()) {
                    throw new IllegalArgumentException("client_id host resolves to a special-use address");
                }
            }
            return null;
        });
    }

    private Future<ClientMetadataDocument> fetchMetadata(String clientId) {
        OAuthProperties oauth = domainProperties.getDomain().getOauth();
        return webClient.getAbs(clientId)
                        .putHeader("Accept", "application/json")
                        .timeout(oauth.getClientMetadataFetchTimeout().toMillis())
                        .send()
                        .map(response -> {
                            if (response.statusCode() != 200) {
                                throw new IllegalArgumentException(
                                        "client metadata document returned HTTP " + response.statusCode());
                            }
                            Buffer body = response.body();
                            if (body == null || body.length() > oauth.getClientMetadataMaxBytes()) {
                                throw new IllegalArgumentException("client metadata document exceeds the size limit");
                            }
                            return validateDocument(clientId, readDocument(body));
                        });
    }

    private ClientMetadataDocument readDocument(Buffer body) {
        try {
            return jsonMapper.readValue(body.getBytes(), ClientMetadataDocument.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("client metadata document is not valid JSON");
        }
    }

    /** Applies the document rules from Section 4.1, returning the document it validated. */
    private static ClientMetadataDocument validateDocument(String clientId, ClientMetadataDocument document) {
        // simple string comparison per RFC 3986 6.2.1 — the document must name the URL it was
        // served from, so a document cannot claim an identity its host does not own
        if (!clientId.equals(document.getClientId())) {
            throw new IllegalArgumentException("client metadata document does not match its client_id");
        }
        // no shared secret can exist for a document-identified client
        String authMethod = document.getTokenEndpointAuthMethod();
        if (authMethod != null && !"none".equals(authMethod)) {
            throw new IllegalArgumentException("client metadata document must use token_endpoint_auth_method none");
        }
        if (document.getClientSecret() != null || document.getClientSecretExpiresAt() != null) {
            throw new IllegalArgumentException("client metadata document must not carry a client secret");
        }
        List<String> redirectUris = document.getRedirectUris();
        if (redirectUris == null || redirectUris.isEmpty()) {
            throw new IllegalArgumentException("client metadata document must register at least one redirect_uri");
        }
        for (String redirectUri : redirectUris) {
            validateRedirectUri(redirectUri);
        }
        // the consent page always has something to show, even for a document that omitted the name
        String clientName = document.getClientName();
        document.setClientName(clientName == null || clientName.isBlank() ? clientId : clientName.trim());
        return document;
    }

    /**
     * A redirect URI must be absolute with an https scheme; plain http is allowed only for
     * loopback hosts (RFC 8252 native clients such as Claude Code's localhost callback).
     * <p>
     * The scheme restriction is load-bearing beyond transport: the consent page navigates to this
     * URI with {@code window.location.href}, so a {@code javascript:} or {@code data:} entry would
     * execute on the SPA's origin when the user approves.
     */
    private static void validateRedirectUri(String redirectUri) {
        URI uri;
        try {
            uri = new URI(redirectUri);
        } catch (Exception e) {
            throw new IllegalArgumentException("redirect_uri is not a valid URI: " + redirectUri);
        }
        if (!"https".equals(uri.getScheme())
                && !("http".equals(uri.getScheme()) && OAuth2Util.isLoopbackHost(uri.getHost()))) {
            throw new IllegalArgumentException("redirect_uri must be https, or http on a loopback host: " + redirectUri);
        }
        if (uri.getFragment() != null) {
            throw new IllegalArgumentException("redirect_uri must not contain a fragment: " + redirectUri);
        }
    }

    private Date cacheExpiry() {
        return new Date(System.currentTimeMillis()
                                + domainProperties.getDomain().getOauth().getClientMetadataCacheTtl().toMillis());
    }

    /** A validated document held until {@code expiresAt}; errors and invalid documents are never cached. */
    private record CachedClient(ClientMetadataDocument document, Date expiresAt) {}
}
