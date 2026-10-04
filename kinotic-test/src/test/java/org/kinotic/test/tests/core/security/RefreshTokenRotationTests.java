package org.kinotic.test.tests.core.security;

import io.vertx.core.Future;
import org.junit.jupiter.api.Test;
import org.kinotic.domain.api.model.security.KinoticAudience;
import org.kinotic.domain.api.model.security.DelegateSession;
import org.kinotic.domain.api.model.security.RefreshTokenRotation;
import org.kinotic.domain.api.services.security.RefreshTokenService;
import org.kinotic.test.support.kinotic.KinoticTestBase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies refresh token rotation consumes a token exactly once, however many presentations of it race: one
 * rotation succeeds, every other fails as reuse, and the family dies with them, the replacement included; and
 * that a rotation on its own keeps the session live while presenting the consumed token again ends it.
 */
@SpringBootTest
public class RefreshTokenRotationTests extends KinoticTestBase {

    // the seeded Kinotic Test user of the kinotic-test organization
    private static final String IDENTITY = "00000000-0000-0000-0000-000000000002";
    private static final int PRESENTATIONS = 8;

    @Autowired
    private RefreshTokenService refreshTokens;

    @Test
    public void concurrentRotationsOfOneTokenConsumeItOnceAndRevokeTheFamily() throws Exception {
        String label = "rotation-race-" + UUID.randomUUID();
        String token = await(refreshTokens.issue(IDENTITY, KinoticAudience.PUBLISHED_SERVICES, label));
        assertTrue(sessionLive(label), "the issued family is a live session");

        // every presentation reads the token before any of them writes, the window the race lives in
        List<Future<RefreshTokenRotation>> rotations = new ArrayList<>();
        for (int i = 0; i < PRESENTATIONS; i++) {
            rotations.add(refreshTokens.rotate(token));
        }
        await(Future.join(rotations).otherwiseEmpty());

        List<Future<RefreshTokenRotation>> succeeded = rotations.stream().filter(Future::succeeded).toList();
        assertEquals(1, succeeded.size(), "exactly one presentation consumes the token");
        for (Future<RefreshTokenRotation> failed : rotations.stream().filter(Future::failed).toList()) {
            assertTrue(failed.cause().getMessage().contains("reuse detected"), "the rest are reuse: " + failed.cause());
        }
        assertTrue(!sessionLive(label), "reuse revokes the family");

        // the replacement the one success handed out died with its family
        String replacement = succeeded.getFirst().result().refreshToken();
        ExecutionException reuse = assertThrows(ExecutionException.class, () -> await(refreshTokens.rotate(replacement)));
        assertTrue(reuse.getCause().getMessage().contains("reuse detected"), reuse.getCause().toString());
    }

    @Test
    public void aRotationOnItsOwnKeepsTheSessionAndTheConsumedTokenEndsIt() throws Exception {
        String label = "rotation-" + UUID.randomUUID();
        String token = await(refreshTokens.issue(IDENTITY, KinoticAudience.PUBLISHED_SERVICES, label));

        RefreshTokenRotation rotation = await(refreshTokens.rotate(token));
        assertEquals(KinoticAudience.PUBLISHED_SERVICES, rotation.audience());
        assertTrue(sessionLive(label), "the family lives on through its replacement");

        ExecutionException reuse = assertThrows(ExecutionException.class, () -> await(refreshTokens.rotate(token)));
        assertTrue(reuse.getCause().getMessage().contains("reuse detected"), reuse.getCause().toString());
        assertTrue(!sessionLive(label), "presenting the consumed token again revokes the family");
    }

    private boolean sessionLive(String label) throws Exception {
        return await(refreshTokens.findActiveSessions(IDENTITY)).stream()
                                                                 .map(DelegateSession::label)
                                                                 .anyMatch(label::equals);
    }

    private static <T> T await(Future<T> future) throws Exception {
        return future.toCompletionStage().toCompletableFuture().get(30, TimeUnit.SECONDS);
    }
}
