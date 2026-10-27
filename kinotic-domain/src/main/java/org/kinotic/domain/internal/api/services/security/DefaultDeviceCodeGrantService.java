package org.kinotic.domain.internal.api.services.security;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.kinotic.domain.api.model.security.DeviceCodeGrantStart;
import org.kinotic.domain.api.model.security.DeviceCodePollResult;
import org.kinotic.domain.api.model.security.PollStatus;
import org.kinotic.domain.api.model.security.participant.ParticipantScope;
import org.kinotic.domain.api.services.security.ParticipantIdentityService;
import org.kinotic.core.api.security.Participant;
import org.kinotic.domain.api.services.security.DeviceCodeGrantService;
import org.kinotic.domain.internal.api.model.DeviceCodeGrant;
import org.kinotic.domain.internal.api.repositories.DeviceCodeGrantRepository;
import org.kinotic.domain.api.utils.DomainUtil;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Date;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultDeviceCodeGrantService implements DeviceCodeGrantService {

    /** How long a started grant remains valid before it must be approved. */
    private static final long GRANT_TTL_MS = 10 * 60 * 1000L;

    /** Minimum seconds the CLI must wait between polls. */
    private static final int POLL_INTERVAL_SECONDS = 5;

    /** Bytes of entropy for the device code — the secret the CLI polls with. */
    private static final int DEVICE_CODE_BYTES = 32;

    /** User-code alphabet: no vowels (avoids forming words) and no visually ambiguous glyphs. */
    private static final char[] USER_CODE_ALPHABET = "BCDFGHJKLMNPQRSTVWXZ".toCharArray();
    private static final int USER_CODE_LENGTH = 8;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final DeviceCodeGrantRepository deviceCodeGrantRepository;
    private final ParticipantIdentityService identityService;

    @Override
    public Future<DeviceCodeGrantStart> start(String issuer, String deviceName) {
        Validate.notBlank(issuer, "issuer is required");
        Date now = new Date();
        String deviceCode = DomainUtil.generateUrlSafeToken(DEVICE_CODE_BYTES);
        String userCode = generateUserCode();
        Date expiresAt = new Date(now.getTime() + GRANT_TTL_MS);

        DeviceCodeGrant grant = new DeviceCodeGrant()
                .setId(UUID.randomUUID().toString())
                .setIssuer(issuer)
                .setDeviceCodeHash(DomainUtil.sha256Hex(deviceCode))
                .setUserCode(userCode)
                .setDeviceName(StringUtils.trimToNull(deviceName))
                .setCreated(now)
                .setExpiresAt(expiresAt)
                .setIntervalSeconds(POLL_INTERVAL_SECONDS);

        return deviceCodeGrantRepository.saveSync(grant)
                .map(new DeviceCodeGrantStart(deviceCode, userCode, expiresAt, POLL_INTERVAL_SECONDS));
    }

    @Override
    public Future<DeviceCodePollResult> poll(String issuer, String deviceCode) {
        Validate.notBlank(issuer, "issuer is required");
        Validate.notBlank(deviceCode, "deviceCode is required");
        return deviceCodeGrantRepository.findByDeviceCodeHash(DomainUtil.sha256Hex(deviceCode))
                                        .compose(grant -> evaluatePoll(issuer, grant));
    }

    private Future<DeviceCodePollResult> evaluatePoll(String issuer, DeviceCodeGrant grant) {
        // a grant another server started is left for that server, as if this one never saw it
        if (grant == null || !issuer.equals(grant.getIssuer())) {
            return Future.succeededFuture(new DeviceCodePollResult(PollStatus.INVALID, null, null));
        }
        Date now = new Date();
        if (grant.getExpiresAt().before(now)) {
            return deviceCodeGrantRepository.deleteById(grant.getId())
                    .map(new DeviceCodePollResult(PollStatus.EXPIRED, null, null));
        }
        if (grant.getIdentityId() != null) {
            // Approved — resolve the approver in the scope it approved from, then consume the grant so it cannot
            // be replayed. The approver is resolved first so a lookup that fails leaves the grant for the next
            // poll; a poll that finds the grant consumed lost it to a concurrent poll, and an approver that may
            // no longer act there, disabled or no longer a member, fails it.
            return identityService.resolveParticipant(grant.getIdentityId(), grant.getOrganizationId(), grant.getApplicationId())
                    .compose(approver -> deviceCodeGrantRepository.consumeByIdSync(grant.getId())
                            .map(consumed -> consumed && approver != null
                                    ? new DeviceCodePollResult(PollStatus.APPROVED, approver, grant.getDeviceName())
                                    : new DeviceCodePollResult(PollStatus.INVALID, null, null)));
        }
        if (polledTooSoon(grant, now)) {
            return Future.succeededFuture(new DeviceCodePollResult(PollStatus.SLOW_DOWN, null, null));
        }
        return deviceCodeGrantRepository.recordPoll(grant.getId(), now)
                .map(new DeviceCodePollResult(PollStatus.AUTHORIZATION_PENDING, null, null));
    }

    @Override
    public Future<Void> approve(String issuer, String userCode, Participant approver) {
        Validate.notBlank(issuer, "issuer is required");
        Validate.notBlank(userCode, "userCode is required");
        Validate.notNull(approver, "approver is required");
        ParticipantScope scope = DomainUtil.scopeOf(approver);
        return deviceCodeGrantRepository.findByUserCode(normalizeUserCode(userCode))
                .compose(grant -> {
                    if (grant == null || !issuer.equals(grant.getIssuer())) {
                        return Future.failedFuture(new IllegalArgumentException("Unknown user code"));
                    }
                    if (grant.getExpiresAt().before(new Date())) {
                        return Future.failedFuture(
                                new IllegalArgumentException("Device authorization request has expired"));
                    }
                    if (grant.getIdentityId() != null) {
                        return Future.failedFuture(
                                new IllegalArgumentException("Device authorization request has already been approved"));
                    }
                    // bound in the shard operation, so of two approvals racing for one grant the second fails as
                    // the already-approved case the check above catches when they are further apart
                    return deviceCodeGrantRepository.approve(grant.getId(), approver.getId(), scope)
                                                    .compose(bound -> bound
                                                            ? Future.<Void>succeededFuture()
                                                            : Future.failedFuture(new IllegalArgumentException(
                                                                    "Device authorization request has already been approved")));
                });
    }

    private boolean polledTooSoon(DeviceCodeGrant grant, Date now) {
        Date last = grant.getLastPolledAt();
        return last != null && (now.getTime() - last.getTime()) < grant.getIntervalSeconds() * 1000L;
    }

    private String generateUserCode() {
        StringBuilder sb = new StringBuilder(USER_CODE_LENGTH + 1);
        for (int i = 0; i < USER_CODE_LENGTH; i++) {
            if (i == USER_CODE_LENGTH / 2) {
                sb.append('-');
            }
            sb.append(USER_CODE_ALPHABET[SECURE_RANDOM.nextInt(USER_CODE_ALPHABET.length)]);
        }
        return sb.toString();
    }

    private String normalizeUserCode(String userCode) {
        return userCode.trim().toUpperCase();
    }
}
