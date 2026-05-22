package org.kinotic.domain.internal.api.services.iam;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.kinotic.domain.api.services.iam.DeviceCodeGrantService;
import org.kinotic.domain.api.services.iam.DeviceCodeGrantStart;
import org.kinotic.domain.api.services.iam.DeviceCodePollResult;
import org.kinotic.domain.api.services.iam.PollStatus;
import org.kinotic.domain.internal.api.model.DeviceCodeGrant;
import org.kinotic.domain.internal.api.repositories.DeviceCodeGrantRepository;
import org.kinotic.domain.internal.api.repositories.IamUserRepository;
import org.kinotic.domain.internal.utils.DomainUtil;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

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
    private final IamUserRepository iamUserRepository;

    @Override
    public CompletableFuture<DeviceCodeGrantStart> start() {
        Date now = new Date();
        String deviceCode = DomainUtil.generateUrlSafeToken(DEVICE_CODE_BYTES);
        String userCode = generateUserCode();
        Date expiresAt = new Date(now.getTime() + GRANT_TTL_MS);

        DeviceCodeGrant grant = new DeviceCodeGrant()
                .setId(UUID.randomUUID().toString())
                .setDeviceCodeHash(DomainUtil.sha256Hex(deviceCode))
                .setUserCode(userCode)
                .setCreated(now)
                .setExpiresAt(expiresAt)
                .setIntervalSeconds(POLL_INTERVAL_SECONDS);

        return deviceCodeGrantRepository.saveSync(grant)
                .thenApply(saved -> new DeviceCodeGrantStart(deviceCode, userCode, expiresAt, POLL_INTERVAL_SECONDS));
    }

    @Override
    public CompletableFuture<DeviceCodePollResult> poll(String deviceCode) {
        Validate.notBlank(deviceCode, "deviceCode is required");
        return deviceCodeGrantRepository.findByDeviceCodeHash(DomainUtil.sha256Hex(deviceCode))
                                        .thenCompose(this::evaluatePoll);
    }

    private CompletableFuture<DeviceCodePollResult> evaluatePoll(DeviceCodeGrant grant) {
        if (grant == null) {
            return CompletableFuture.completedFuture(new DeviceCodePollResult(PollStatus.INVALID, null));
        }
        Date now = new Date();
        if (grant.getExpiresAt().before(now)) {
            return deviceCodeGrantRepository.deleteById(grant.getId())
                    .thenApply(v -> new DeviceCodePollResult(PollStatus.EXPIRED, null));
        }
        if (grant.getUserId() != null) {
            // Approved — hand back the user and consume the grant so it cannot be replayed.
            return iamUserRepository.findById(grant.getUserId())
                    .thenCompose(user -> deviceCodeGrantRepository.deleteById(grant.getId())
                            .thenApply(v -> (user == null || !user.isEnabled())
                                    ? new DeviceCodePollResult(PollStatus.INVALID, null)
                                    : new DeviceCodePollResult(PollStatus.APPROVED, user)));
        }
        if (polledTooSoon(grant, now)) {
            return CompletableFuture.completedFuture(new DeviceCodePollResult(PollStatus.SLOW_DOWN, null));
        }
        grant.setLastPolledAt(now);
        return deviceCodeGrantRepository.saveSync(grant)
                .thenApply(v -> new DeviceCodePollResult(PollStatus.AUTHORIZATION_PENDING, null));
    }

    @Override
    public CompletableFuture<Void> approve(String userCode, String userId) {
        Validate.notBlank(userCode, "userCode is required");
        Validate.notBlank(userId, "userId is required");
        return deviceCodeGrantRepository.findByUserCode(normalizeUserCode(userCode))
                .thenCompose(grant -> {
                    if (grant == null) {
                        return CompletableFuture.failedFuture(new IllegalArgumentException("Unknown user code"));
                    }
                    if (grant.getExpiresAt().before(new Date())) {
                        return CompletableFuture.failedFuture(
                                new IllegalArgumentException("Device authorization request has expired"));
                    }
                    if (grant.getUserId() != null) {
                        return CompletableFuture.failedFuture(
                                new IllegalArgumentException("Device authorization request has already been approved"));
                    }
                    grant.setUserId(userId);
                    return deviceCodeGrantRepository.saveSync(grant).thenApply(v -> null);
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
