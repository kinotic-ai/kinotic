package org.kinotic.core.internal.security;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.JWTOptions;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.authentication.TokenCredentials;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.core.api.config.VersionedKeySet;
import org.kinotic.core.api.security.KinoticJwtConstants;
import org.kinotic.core.internal.platform.PlatformSecretsService;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Mints and validates Kinotic-issued JWTs signed with HS256. The signing keys are
 * loaded from {@link PlatformSecretsService} and rotated transparently when the underlying
 * file changes. Tokens are stamped with the active {@code kid} so validation can dispatch
 * to any key in the current set — enabling rotation windows without invalidating in-flight
 * tokens.
 * <p>
 * Kinotic JWTs are short-lived (seconds) tickets used to authenticate a STOMP CONNECT;
 * after that, the gateway's {@link org.kinotic.core.api.security.SessionManager} owns the
 * session lifecycle.
 */
@Slf4j
@Component
public class KinoticJwtIssuer {

    private static final String ALGORITHM = "HS256";
    private static final String KEY_TYPE = "oct";

    private final Vertx vertx;
    private final PlatformSecretsService platformSecretsService;
    private final AtomicReference<State> state = new AtomicReference<>();

    public KinoticJwtIssuer(Vertx vertx, PlatformSecretsService platformSecretsService) {
        this.vertx = vertx;
        this.platformSecretsService = platformSecretsService;
    }

    @PostConstruct
    public void start() {
        VersionedKeySet initial = platformSecretsService.getJwtSigningKeys();
        if (initial == null) {
            log.warn("No JWT signing keys mounted; KinoticJwtIssuer will not be able to mint or validate tokens");
            return;
        }
        state.set(build(initial));
        platformSecretsService.addJwtSigningKeysListener(updated -> {
            log.info("Rotating JWT signing keys; active={}", updated.getActiveKeyId());
            state.set(build(updated));
        });
    }

    /**
     * Mints a JWT containing the given claims, signed with the currently active key.
     * Always sets {@code aud=kinotic} (overwriting any caller-supplied value) and stamps
     * the active {@code kid} into the header so validators can dispatch to the right key.
     */
    public String sign(JsonObject claims, JWTOptions options) {
        State current = requireState();
        JWTOptions withDefaults = options
                .setAlgorithm(ALGORITHM)
                .setAudience(List.of(KinoticJwtConstants.AUDIENCE))
                .setHeader(new JsonObject().put("kid", current.activeKeyId));
        return current.jwtAuth.generateToken(claims, withDefaults);
    }

    /**
     * Validates a token signed by any key currently in the active set and confirms its
     * audience is {@value KinoticJwtConstants#AUDIENCE}. Tokens minted by an IdP (or any
     * other party) will fail the audience check even if they happened to share signing
     * material.
     */
    public Future<User> authenticate(String token) {
        State current = requireState();
        return current.jwtAuth.authenticate(new TokenCredentials(token))
                              .compose(KinoticJwtIssuer::requireKinoticAudience);
    }

    private static Future<User> requireKinoticAudience(User user) {
        Object aud = user.principal().getValue("aud");
        boolean ok = switch (aud) {
            case String s -> KinoticJwtConstants.AUDIENCE.equals(s);
            case io.vertx.core.json.JsonArray arr -> arr.contains(KinoticJwtConstants.AUDIENCE);
            case null, default -> false;
        };
        return ok ? Future.succeededFuture(user)
                  : Future.failedFuture(new SecurityException("JWT audience is not '" + KinoticJwtConstants.AUDIENCE + "'"));
    }

    private State requireState() {
        State s = state.get();
        if (s == null) {
            throw new IllegalStateException(
                    "JWT signing keys not mounted — set kinotic.platformSecrets.jwtSigningKeysPath");
        }
        return s;
    }

    private State build(VersionedKeySet set) {
        JWTAuthOptions options = new JWTAuthOptions();
        for (VersionedKeySet.KeyEntry entry : set.getKeys()) {
            options.addJwk(new JsonObject()
                                   .put("kty", KEY_TYPE)
                                   .put("kid", entry.getId())
                                   .put("alg", ALGORITHM)
                                   .put("k", toBase64Url(entry.getKey())));
        }
        return new State(set.getActiveKeyId(), JWTAuth.create(vertx, options));
    }

    /** Converts standard base64 (from platform secret files) to base64url required by JWK. */
    private static String toBase64Url(String base64) {
        byte[] raw = Base64.getDecoder().decode(base64);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

    private record State(String activeKeyId, JWTAuth jwtAuth) {}
}
