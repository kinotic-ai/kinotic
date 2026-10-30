package org.kinotic.test.tests.core.security;

import io.vertx.core.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kinotic.core.api.security.KinoticAudience;
import org.kinotic.core.api.security.Participant;
import org.kinotic.domain.api.model.Organization;
import org.kinotic.domain.api.model.security.DeviceCodeGrantStart;
import org.kinotic.domain.api.model.security.DeviceCodePollResult;
import org.kinotic.domain.api.model.security.PollStatus;
import org.kinotic.domain.api.model.security.RefreshTokenRotation;
import org.kinotic.domain.api.model.security.identity.OrganizationParticipantIdentity;
import org.kinotic.domain.api.services.OrganizationService;
import org.kinotic.domain.api.services.security.DeviceCodeGrantService;
import org.kinotic.domain.api.services.security.ParticipantIdentityService;
import org.kinotic.domain.api.services.security.RefreshTokenService;
import org.kinotic.test.support.kinotic.KinoticTestBase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The grants a client redeems once: a refresh token rotates into exactly one replacement, however many
 * rotations present it at once, and presenting it again revokes its whole family; an approved device code is
 * redeemed by exactly one poll; and deleting the identity revokes every token it holds.
 */
@SpringBootTest
public class OAuthGrantTests extends KinoticTestBase {

    private static final String ISSUER = "https://test.invalid";
    private static final String PASSWORD = "Correct-Horse-Battery-9";

    @Autowired
    private RefreshTokenService refreshTokens;

    @Autowired
    private DeviceCodeGrantService deviceCodes;

    @Autowired
    private ParticipantIdentityService identities;

    @Autowired
    private OrganizationService organizations;

    private OrganizationParticipantIdentity user;

    @BeforeEach
    public void createUser() throws Exception {
        Organization organization = await(organizations.create(new Organization().setName("OAuth grant tests " + suffix())
                                                                                 .setDescription("Organization of one OAuth grant test")));
        OrganizationParticipantIdentity created = new OrganizationParticipantIdentity();
        created.setOrganizationId(organization.getId());
        created.setEmail("owner-" + suffix() + "@example.test");
        created.setDisplayName("Token owner");
        user = await(identities.createUser(created, PASSWORD));
    }

    @Test
    public void aRefreshTokenRotatesOnceAndItsReuseRevokesTheFamily() throws Exception {
        String first = await(refreshTokens.issue(user.getId(), KinoticAudience.PUBLISHED_SERVICES, "laptop"));
        RefreshTokenRotation rotation = await(refreshTokens.rotate(first));
        assertNotEquals(first, rotation.refreshToken());
        assertEquals(KinoticAudience.PUBLISHED_SERVICES, rotation.audience());
        assertEquals(1, await(refreshTokens.findActiveSessions(user.getId())).size());

        // the rotated token is reused: the lineage is compromised and the replacement dies with it
        assertTrue(failure(() -> refreshTokens.rotate(first)).getMessage().contains("reuse"));
        assertTrue(failure(() -> refreshTokens.rotate(rotation.refreshToken())).getMessage().contains("reuse"));
        assertTrue(await(refreshTokens.findActiveSessions(user.getId())).isEmpty());
    }

    @Test
    public void twoRotationsOfOneRefreshTokenAtOnceMintAtMostOneReplacement() throws Exception {
        String token = await(refreshTokens.issue(user.getId(), KinoticAudience.PUBLISHED_SERVICES, null));
        Future<RefreshTokenRotation> one = refreshTokens.rotate(token);
        Future<RefreshTokenRotation> other = refreshTokens.rotate(token);
        Future.join(one, other).toCompletionStage().toCompletableFuture().handle((v, e) -> null).get(30, TimeUnit.SECONDS);
        assertTrue(one.succeeded() ^ other.succeeded(), "exactly one rotation consumes the token");
    }

    @Test
    public void deletingTheIdentityRevokesItsRefreshTokens() throws Exception {
        String token = await(refreshTokens.issue(user.getId(), KinoticAudience.MCP_TOOLS, "host"));
        await(identities.deleteById(user.getId()));
        assertTrue(await(refreshTokens.findActiveSessions(user.getId())).isEmpty());
        assertThrows(ExecutionException.class, () -> await(refreshTokens.rotate(token)));
    }

    @Test
    public void anApprovedDeviceCodeIsRedeemedByExactlyOnePoll() throws Exception {
        DeviceCodeGrantStart started = await(deviceCodes.start(ISSUER, "laptop"));
        Participant approver = await(identities.resolveParticipant(user, user.getOrganizationId(), null));
        await(deviceCodes.approve(ISSUER, started.userCode(), approver));

        DeviceCodePollResult approved = await(deviceCodes.poll(ISSUER, started.deviceCode()));
        assertEquals(PollStatus.APPROVED, approved.status());
        assertEquals(approver.getId(), approved.approver().getId());
        assertEquals("laptop", approved.deviceName());

        DeviceCodePollResult again = await(deviceCodes.poll(ISSUER, started.deviceCode()));
        assertEquals(PollStatus.INVALID, again.status());
        assertNull(again.approver());
    }

    private Throwable failure(Supplier<Future<?>> call) {
        return assertThrows(ExecutionException.class, () -> await(call.get())).getCause();
    }
}
