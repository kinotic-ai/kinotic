package org.kinotic.test.tests.core.security;

import io.vertx.core.Future;
import org.junit.jupiter.api.Test;
import org.kinotic.domain.api.model.security.DeviceCodeGrantStart;
import org.kinotic.domain.api.model.security.DeviceCodePollResult;
import org.kinotic.domain.api.model.security.PollStatus;
import org.kinotic.domain.api.services.security.DeviceCodeGrantService;
import org.kinotic.test.support.kinotic.KinoticTestBase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies a device-code grant is redeemed exactly once however many polls race for it once it is approved, the
 * rest polling as invalid, and that of two approvals racing for one grant exactly one binds its identity, the
 * one a poll then redeems.
 */
@SpringBootTest
public class DeviceCodeGrantRaceTests extends KinoticTestBase {

    private static final String ISSUER = "https://kinotic.test/device-code-race";
    // the seeded System Admin and Kinotic Test users
    private static final String ADMIN = "00000000-0000-0000-0000-000000000001";
    private static final String USER = "00000000-0000-0000-0000-000000000002";
    private static final int POLLS = 8;

    @Autowired
    private DeviceCodeGrantService grants;

    @Test
    public void concurrentPollsRedeemAnApprovedGrantOnce() throws Exception {
        DeviceCodeGrantStart started = await(grants.start(ISSUER, "race-device"));
        await(grants.approve(ISSUER, started.userCode(), USER));

        // every poll reads the grant approved before any of them consumes it, the window the race lives in
        List<Future<DeviceCodePollResult>> polls = new ArrayList<>();
        for (int i = 0; i < POLLS; i++) {
            polls.add(grants.poll(ISSUER, started.deviceCode()));
        }
        await(Future.join(polls).otherwiseEmpty());

        List<DeviceCodePollResult> results = polls.stream().map(poll -> {
            assertTrue(poll.succeeded(), "a poll failed: " + poll.cause());
            return poll.result();
        }).toList();
        List<DeviceCodePollResult> approved = results.stream().filter(r -> r.status() == PollStatus.APPROVED).toList();
        assertEquals(1, approved.size(), "exactly one poll redeems the grant: " + results.stream().map(DeviceCodePollResult::status).toList());
        assertEquals(USER, approved.getFirst().user().getId());
        assertEquals("race-device", approved.getFirst().deviceName());
        assertEquals(POLLS - 1, results.stream().filter(r -> r.status() == PollStatus.INVALID).count(), "the rest find it gone");

        assertEquals(PollStatus.INVALID, await(grants.poll(ISSUER, started.deviceCode())).status(), "a redeemed grant stays gone");
    }

    @Test
    public void concurrentApprovalsBindAGrantOnce() throws Exception {
        DeviceCodeGrantStart started = await(grants.start(ISSUER, null));

        Future<Void> byAdmin = grants.approve(ISSUER, started.userCode(), ADMIN);
        Future<Void> byUser = grants.approve(ISSUER, started.userCode(), USER);
        await(Future.join(byAdmin, byUser).otherwiseEmpty());

        assertTrue(byAdmin.succeeded() != byUser.succeeded(), "exactly one approval binds the grant");
        Future<Void> lost = byAdmin.succeeded() ? byUser : byAdmin;
        assertTrue(lost.cause().getMessage().contains("already been approved"), lost.cause().toString());

        DeviceCodePollResult redeemed = await(grants.poll(ISSUER, started.deviceCode()));
        assertEquals(PollStatus.APPROVED, redeemed.status());
        assertEquals(byAdmin.succeeded() ? ADMIN : USER, redeemed.user().getId(), "the poll redeems the identity that won");
    }

    private static <T> T await(Future<T> future) throws Exception {
        return future.toCompletionStage().toCompletableFuture().get(30, TimeUnit.SECONDS);
    }
}
