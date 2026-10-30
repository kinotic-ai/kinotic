package org.kinotic.domain.api.services.security;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.JWTOptions;
import io.vertx.ext.auth.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kinotic.core.api.exceptions.AuthenticationException;
import org.kinotic.core.api.security.KinoticAudience;
import org.kinotic.domain.api.config.KinoticDomainProperties;
import org.kinotic.domain.api.config.VersionedKey;
import org.kinotic.domain.api.config.VersionedKeySet;
import org.kinotic.domain.internal.api.services.secret.SystemSecretsService;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A token is accepted only by the surface it was minted for: the {@code aud} claim the issuer stamps is checked
 * against the audience the authenticating surface names, after the signature.
 */
class KinoticJwtIssuerTest {

    @TempDir
    Path directory;

    private Vertx vertx;
    private SystemSecretsService secrets;
    private KinoticJwtIssuer issuer;

    @BeforeEach
    void start() throws Exception {
        Path file = directory.resolve("jwt-signing-keys");
        JsonMapper jsonMapper = JsonMapper.builder().build();
        VersionedKeySet set = new VersionedKeySet().setActiveKeyId("v1")
                                                   .setKeys(List.of(new VersionedKey("v1", Base64.getEncoder().encodeToString(new byte[32]))));
        Files.write(file, jsonMapper.writeValueAsBytes(set));
        KinoticDomainProperties properties = new KinoticDomainProperties();
        properties.getDomain().getSystemSecrets().setJwtSigningKeysPath(file);
        secrets = new SystemSecretsService(properties, jsonMapper, Optional.empty());
        secrets.start();
        vertx = Vertx.vertx();
        issuer = new KinoticJwtIssuer(vertx, secrets);
        issuer.start();
    }

    @AfterEach
    void stop() throws Exception {
        secrets.stop();
        vertx.close().toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
    }

    @Test
    void aTokenAuthenticatesAtTheSurfaceItWasMintedFor() throws Exception {
        String token = issuer.sign(new JsonObject().put("sub", "delegate-1"), new JWTOptions(), KinoticAudience.MCP_TOOLS);
        User user = await(issuer.authenticate(token, KinoticAudience.MCP_TOOLS));
        assertEquals("delegate-1", user.principal().getString("sub"));
    }

    @Test
    void aTokenMintedForAnotherSurfaceIsRefused() {
        String token = issuer.sign(new JsonObject().put("sub", "delegate-1"), new JWTOptions(), KinoticAudience.MCP_TOOLS);
        ExecutionException refusal = assertThrows(ExecutionException.class,
                                                  () -> await(issuer.authenticate(token, KinoticAudience.PUBLISHED_SERVICES)));
        assertInstanceOf(AuthenticationException.class, refusal.getCause());
    }

    private static <T> T await(Future<T> future) throws Exception {
        return future.toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
    }
}
