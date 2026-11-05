package org.kinotic.test.tests.core.security;

import io.vertx.core.Future;
import org.junit.jupiter.api.Test;
import org.kinotic.core.api.security.Participant;
import org.kinotic.domain.api.model.security.CodeExchangeResult;
import org.kinotic.domain.api.model.security.DelegateSession;
import org.kinotic.domain.api.model.security.DeviceCodeGrantStart;
import org.kinotic.domain.api.model.security.DeviceCodePollResult;
import org.kinotic.domain.api.model.security.KinoticAudience;
import org.kinotic.domain.api.model.security.PollStatus;
import org.kinotic.domain.api.model.security.RefreshTokenRotation;
import org.kinotic.domain.api.rest.support.OAuth2Util;
import org.kinotic.domain.api.services.security.DeviceCodeGrantService;
import org.kinotic.domain.api.services.security.OAuthAuthorizationService;
import org.kinotic.domain.api.services.security.ParticipantIdentityService;
import org.kinotic.domain.api.services.security.RefreshTokenService;
import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.domain.internal.api.model.OAuthAuthorizationGrant;
import org.kinotic.domain.internal.api.repositories.OAuthAuthorizationGrantRepository;
import org.kinotic.test.support.kinotic.KinoticTestBase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies each single-use credential is redeemed exactly once however many presentations of it race: a refresh
 * token rotates once and the rest are treated as reuse, an approved device-code grant is redeemed by one poll, a
 * device-code grant binds the first of two racing approvers and keeps that approval through polls racing it, and an
 * authorization code is exchanged once.
 */
@SpringBootTest
public class SingleUseTokenRaceTests extends KinoticTestBase {

    private static final String ISSUER = "https://kinotic.test/single-use-token-race";
    // the seeded System Admin and the seeded Kinotic Test organization user, a member of test-org
    private static final String SYSTEM_USER = "00000000-0000-0000-0000-000000000001";
    private static final String ORGANIZATION_USER = "00000000-0000-0000-0000-000000000002";
    private static final int PRESENTATIONS = 8;

    @Autowired
    private RefreshTokenService refreshTokens;

    @Autowired
    private DeviceCodeGrantService deviceCodeGrants;

    @Autowired
    private OAuthAuthorizationService oauthAuthorizations;

    @Autowired
    private OAuthAuthorizationGrantRepository oauthGrantRepository;

    @Autowired
    private ParticipantIdentityService identityService;

    @Test
    public void concurrentRotationsOfOneRefreshTokenConsumeItOnceAndRevokeTheFamily() {
        String label = "rotation-race-" + UUID.randomUUID();
        String token = refreshTokens.issue(ORGANIZATION_USER, KinoticAudience.PUBLISHED_SERVICES, label).await();
        assertTrue(sessionLive(label), "the issued family is a live session");

        // every presentation reads the token before any of them writes, the window the race lives in
        List<Future<RefreshTokenRotation>> rotations = new ArrayList<>();
        for (int i = 0; i < PRESENTATIONS; i++) {
            rotations.add(refreshTokens.rotate(token));
        }
        Future.join(rotations).otherwiseEmpty().await();

        List<Future<RefreshTokenRotation>> succeeded = rotations.stream().filter(Future::succeeded).toList();
        assertEquals(1, succeeded.size(), "exactly one presentation consumes the token");
        for (Future<RefreshTokenRotation> failed : rotations.stream().filter(Future::failed).toList()) {
            assertTrue(failed.cause().getMessage().contains("reuse detected"), "the rest are reuse: " + failed.cause());
        }
        assertFalse(sessionLive(label), "reuse revokes the family");

        // the replacement the one success handed out is revoked with its family
        Future<RefreshTokenRotation> replacement = refreshTokens.rotate(succeeded.getFirst().result().refreshToken());
        replacement.otherwiseEmpty().await();
        assertTrue(replacement.failed() && replacement.cause().getMessage().contains("reuse detected"),
                   "the replacement is revoked: " + replacement.cause());
    }

    @Test
    public void aRefreshTokenRotationKeepsTheSessionAndPresentingTheConsumedTokenEndsIt() {
        String label = "rotation-" + UUID.randomUUID();
        String token = refreshTokens.issue(ORGANIZATION_USER, KinoticAudience.PUBLISHED_SERVICES, label).await();

        RefreshTokenRotation rotation = refreshTokens.rotate(token).await();
        assertEquals(KinoticAudience.PUBLISHED_SERVICES, rotation.audience());
        assertTrue(sessionLive(label), "the family lives on through its replacement");

        Future<RefreshTokenRotation> reuse = refreshTokens.rotate(token);
        reuse.otherwiseEmpty().await();
        assertTrue(reuse.failed() && reuse.cause().getMessage().contains("reuse detected"),
                   "the consumed token is reuse: " + reuse.cause());
        assertFalse(sessionLive(label), "presenting the consumed token again revokes the family");
    }

    @Test
    public void concurrentPollsRedeemAnApprovedDeviceCodeGrantOnce() {
        DeviceCodeGrantStart started = deviceCodeGrants.start(ISSUER, "race-device").await();
        deviceCodeGrants.approve(ISSUER, started.userCode(), organizationUser()).await();

        // every poll reads the grant approved before any of them consumes it, the window the race lives in
        List<Future<DeviceCodePollResult>> polls = new ArrayList<>();
        for (int i = 0; i < PRESENTATIONS; i++) {
            polls.add(deviceCodeGrants.poll(ISSUER, started.deviceCode()));
        }
        Future.join(polls).otherwiseEmpty().await();

        List<DeviceCodePollResult> results = polls.stream().map(poll -> {
            assertTrue(poll.succeeded(), "a poll failed: " + poll.cause());
            return poll.result();
        }).toList();
        List<DeviceCodePollResult> approved = results.stream().filter(r -> r.status() == PollStatus.APPROVED).toList();
        assertEquals(1, approved.size(), "exactly one poll redeems the grant: "
                + results.stream().map(DeviceCodePollResult::status).toList());
        assertEquals(ORGANIZATION_USER, approved.getFirst().approver().getId());
        assertEquals("race-device", approved.getFirst().deviceName());
        assertEquals(PRESENTATIONS - 1, results.stream().filter(r -> r.status() == PollStatus.INVALID).count(),
                     "the rest find it gone");

        assertEquals(PollStatus.INVALID, deviceCodeGrants.poll(ISSUER, started.deviceCode()).await().status(),
                     "a redeemed grant stays gone");
    }

    @Test
    public void concurrentApprovalsBindADeviceCodeGrantOnce() {
        DeviceCodeGrantStart started = deviceCodeGrants.start(ISSUER, null).await();

        Future<Void> bySystemUser = deviceCodeGrants.approve(ISSUER, started.userCode(), systemUser());
        Future<Void> byOrganizationUser = deviceCodeGrants.approve(ISSUER, started.userCode(), organizationUser());
        Future.join(bySystemUser, byOrganizationUser).otherwiseEmpty().await();

        assertTrue(bySystemUser.succeeded() != byOrganizationUser.succeeded(), "exactly one approval binds the grant");
        Future<Void> lost = bySystemUser.succeeded() ? byOrganizationUser : bySystemUser;
        assertTrue(lost.cause().getMessage().contains("already been approved"), lost.cause().toString());

        // the scope bound with the winning identity is its own, so the redeemed approver resolves in it
        DeviceCodePollResult redeemed = deviceCodeGrants.poll(ISSUER, started.deviceCode()).await();
        assertEquals(PollStatus.APPROVED, redeemed.status());
        assertEquals(bySystemUser.succeeded() ? SYSTEM_USER : ORGANIZATION_USER, redeemed.approver().getId(),
                     "the poll redeems the identity that won");
    }

    @Test
    public void aPendingPollRacingAnApprovalKeepsTheApproval() {
        for (int round = 0; round < 10; round++) {
            DeviceCodeGrantStart started = deviceCodeGrants.start(ISSUER, null).await();

            // each poll reads the grant pending, and writes when it was polled while the approval lands; two
            // polls stay within the conflict retries a single grant's writers get
            List<Future<?>> requests = new ArrayList<>();
            Future<Void> approval = deviceCodeGrants.approve(ISSUER, started.userCode(), organizationUser());
            requests.add(approval);
            for (int i = 0; i < 2; i++) {
                requests.add(deviceCodeGrants.poll(ISSUER, started.deviceCode()));
            }
            Future.join(requests).otherwiseEmpty().await();
            assertTrue(approval.succeeded(), "the approval binds the grant: " + approval.cause());

            DeviceCodePollResult redeemed = deviceCodeGrants.poll(ISSUER, started.deviceCode()).await();
            assertEquals(PollStatus.APPROVED, redeemed.status(), "the grant is still approved after the polls in round " + round);
        }
    }

    @Test
    public void concurrentExchangesOfOneAuthorizationCodeRedeemItOnce() {
        String code = DomainUtil.generateUrlSafeToken(32);
        String verifier = DomainUtil.generateUrlSafeToken(32);
        String clientId = "https://client.kinotic.test/metadata.json";
        String redirectUri = "http://localhost/callback";
        // the grant as approve() leaves it; starting the flow needs a client metadata document served over https
        Date now = new Date();
        OAuthAuthorizationGrant grant = new OAuthAuthorizationGrant()
                .setId(UUID.randomUUID().toString())
                .setIssuer(ISSUER)
                .setClientId(clientId)
                .setClientName("Race Client")
                .setRedirectUri(redirectUri)
                .setCodeChallenge(OAuth2Util.s256Challenge(verifier))
                .setIdentityId(ORGANIZATION_USER)
                .setOrganizationId(TEST_ORG_ID)
                .setCodeHash(DomainUtil.sha256Hex(code))
                .setCreated(now)
                .setExpiresAt(new Date(now.getTime() + 60_000));
        oauthGrantRepository.saveSync(grant).await();

        List<Future<CodeExchangeResult>> exchanges = new ArrayList<>();
        for (int i = 0; i < PRESENTATIONS; i++) {
            exchanges.add(oauthAuthorizations.exchangeCode(ISSUER, code, clientId, redirectUri, verifier));
        }
        Future.join(exchanges).otherwiseEmpty().await();

        List<Future<CodeExchangeResult>> succeeded = exchanges.stream().filter(Future::succeeded).toList();
        assertEquals(1, succeeded.size(), "exactly one exchange redeems the code");
        assertEquals(ORGANIZATION_USER, succeeded.getFirst().result().approver().getId());
        for (Future<CodeExchangeResult> failed : exchanges.stream().filter(Future::failed).toList()) {
            assertTrue(failed.cause().getMessage().contains("Unknown authorization code"),
                       "the rest find the code gone: " + failed.cause());
        }
    }

    private Participant systemUser() {
        return identityService.resolveParticipant(SYSTEM_USER, null, null).await();
    }

    private Participant organizationUser() {
        return identityService.resolveParticipant(ORGANIZATION_USER, TEST_ORG_ID, null).await();
    }

    private boolean sessionLive(String label) {
        return refreshTokens.findActiveSessions(ORGANIZATION_USER).await()
                            .stream()
                            .map(DelegateSession::label)
                            .anyMatch(label::equals);
    }
}
