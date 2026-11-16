package org.kinotic.common.api.oidc;

import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.math.BigInteger;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * A sign-in provider on a local port. It answers as an OpenID Connect provider (discovery document, JWKS, token
 * endpoint issuing a signed id token) and as a GitHub-style provider with no id token (token endpoint under
 * {@code /login/oauth}, {@code /user} and {@code /user/emails}).
 */
class FakeIdentityProvider implements AutoCloseable {

    static final String CLIENT_ID = "test-client";
    static final String SUBJECT = "subject-1";
    static final String EMAIL = "person@example.com";

    private static final String CODE = "test-code";
    private static final String ACCESS_TOKEN = "test-access-token";
    private static final String KEY_ID = "test-key";
    private static final Base64.Encoder BASE64_URL = Base64.getUrlEncoder().withoutPadding();

    private final KeyPair keyPair;
    private final HttpServer server;
    // what the browser's visit to the authorization endpoint carried, as a real provider keeps it for the token endpoint
    private Map<String, String> authorizationRequest = Map.of();
    private long idTokenLifetimeSeconds = 300;

    FakeIdentityProvider(Vertx vertx) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        this.keyPair = generator.generateKeyPair();
        this.server = vertx.createHttpServer()
                           .requestHandler(this::handle)
                           .listen(0, "localhost")
                           .toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
    }

    String baseUrl() {
        return "http://localhost:" + server.actualPort();
    }

    /**
     * Stands in for the browser following {@code authorizationUrl} and the person approving the sign-in.
     *
     * @return the authorization code the provider redirects back with
     */
    String approve(String authorizationUrl) {
        Map<String, String> params = new HashMap<>();
        for (String pair : URI.create(authorizationUrl).getRawQuery().split("&")) {
            String[] parts = pair.split("=", 2);
            params.put(parts[0], URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
        }
        this.authorizationRequest = params;
        return CODE;
    }

    /** Sets how long after it is issued an id token expires; negative for one that is issued expired. */
    void idTokenLifetimeSeconds(long seconds) {
        this.idTokenLifetimeSeconds = seconds;
    }

    /** The value the last approved authorization URL carried for {@code parameter}. */
    String authorizationParameter(String parameter) {
        return authorizationRequest.get(parameter);
    }

    @Override
    public void close() throws Exception {
        server.close().toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
    }

    private void handle(HttpServerRequest request) {
        switch (request.path()) {
            case "/.well-known/openid-configuration" -> json(request, new JsonObject()
                    .put("issuer", baseUrl())
                    .put("authorization_endpoint", baseUrl() + "/authorize")
                    .put("token_endpoint", baseUrl() + "/token")
                    .put("jwks_uri", baseUrl() + "/jwks")
                    .put("response_types_supported", new JsonArray().add("code"))
                    .put("subject_types_supported", new JsonArray().add("public"))
                    .put("id_token_signing_alg_values_supported", new JsonArray().add("RS256")).encode());
            case "/jwks" -> json(request, new JsonObject().put("keys", new JsonArray().add(jwk())).encode());
            case "/token" -> redeem(request, true);
            case "/login/oauth/access_token" -> redeem(request, false);
            case "/user" -> api(request, new JsonObject().put("id", 42).put("login", "octocat")
                                                         .put("name", "The Octocat").putNull("email").encode());
            case "/user/emails" -> api(request, new JsonArray()
                    .add(new JsonObject().put("email", "unverified@example.com").put("primary", false).put("verified", false))
                    .add(new JsonObject().put("email", "other@example.com").put("primary", false).put("verified", true))
                    .add(new JsonObject().put("email", EMAIL).put("primary", true).put("verified", true)).encode());
            default -> request.response().setStatusCode(404).end();
        }
    }

    // the token endpoint: redeems the code only for the redirect URI and PKCE verifier of the approved authorization
    private void redeem(HttpServerRequest request, boolean withIdToken) {
        request.setExpectMultipart(true);
        request.endHandler(v -> {
            MultiMap form = request.formAttributes();
            boolean valid = CODE.equals(form.get("code"))
                    && authorizationRequest.get("redirect_uri").equals(form.get("redirect_uri"))
                    && authorizationRequest.get("code_challenge").equals(OAuth2Util.s256Challenge(form.get("code_verifier")));
            if (valid) {
                JsonObject tokens = new JsonObject().put("access_token", ACCESS_TOKEN).put("token_type", "Bearer");
                if (withIdToken) {
                    tokens.put("expires_in", 3600).put("id_token", idToken());
                }
                json(request, tokens.encode());
            } else {
                request.response().setStatusCode(400).putHeader("Content-Type", "application/json")
                       .end(new JsonObject().put("error", "invalid_grant").encode());
            }
        });
    }

    // an identity endpoint: like api.github.com, it refuses a request with no User-Agent
    private void api(HttpServerRequest request, String body) {
        if (!("Bearer " + ACCESS_TOKEN).equals(request.getHeader("Authorization"))) {
            request.response().setStatusCode(401).end();
        } else if (request.getHeader("User-Agent") == null) {
            request.response().setStatusCode(403).end();
        } else {
            json(request, body);
        }
    }

    private static void json(HttpServerRequest request, String body) {
        request.response().putHeader("Content-Type", "application/json").end(body);
    }

    private String idToken() {
        long now = Instant.now().getEpochSecond();
        JsonObject header = new JsonObject().put("alg", "RS256").put("typ", "JWT").put("kid", KEY_ID);
        JsonObject claims = new JsonObject()
                .put("iss", baseUrl())
                .put("aud", CLIENT_ID)
                .put("sub", SUBJECT)
                .put("email", EMAIL)
                .put("email_verified", true)
                .put("nonce", authorizationRequest.get("nonce"))
                .put("iat", now)
                .put("exp", now + idTokenLifetimeSeconds);
        String signed = BASE64_URL.encodeToString(header.encode().getBytes(StandardCharsets.UTF_8)) + '.'
                + BASE64_URL.encodeToString(claims.encode().getBytes(StandardCharsets.UTF_8));
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(keyPair.getPrivate());
            signature.update(signed.getBytes(StandardCharsets.US_ASCII));
            return signed + '.' + BASE64_URL.encodeToString(signature.sign());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private JsonObject jwk() {
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        return new JsonObject().put("kty", "RSA").put("use", "sig").put("alg", "RS256").put("kid", KEY_ID)
                               .put("n", unsigned(publicKey.getModulus()))
                               .put("e", unsigned(publicKey.getPublicExponent()));
    }

    // a JWK integer is the big-endian magnitude with no sign byte (RFC 7518 §2)
    private static String unsigned(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) {
            bytes = Arrays.copyOfRange(bytes, 1, bytes.length);
        }
        return BASE64_URL.encodeToString(bytes);
    }

}
