package org.kinotic.domain.internal.api.services.security;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.security.KinoticAudience;
import org.kinotic.domain.api.model.security.DelegateSession;
import org.kinotic.domain.api.model.security.RefreshTokenRotation;
import org.kinotic.domain.api.services.security.RefreshTokenService;
import org.kinotic.domain.internal.api.model.RefreshToken;
import org.kinotic.domain.internal.api.repositories.ParticipantIdentityRepository;
import org.kinotic.domain.internal.api.repositories.RefreshTokenRepository;
import org.kinotic.domain.api.utils.DomainUtil;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultRefreshTokenService implements RefreshTokenService {

    /** How long an issued refresh token stays valid. Each rotation issues a fresh token with this TTL. */
    private static final long TOKEN_TTL_MS = 90L * 24 * 60 * 60 * 1000L;

    /** Bytes of entropy for the refresh token. */
    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository refreshTokenRepository;
    private final ParticipantIdentityRepository identityRepository;

    @Override
    public Future<String> issue(String identityId, KinoticAudience audience, String label) {
        Validate.notBlank(identityId, "identityId is required");
        Validate.notNull(audience, "audience is required");
        return mint(UUID.randomUUID().toString(), identityId, UUID.randomUUID().toString(), audience, StringUtils.trimToNull(label));
    }

    @Override
    public Future<List<DelegateSession>> findActiveSessions(String identityId) {
        Validate.notBlank(identityId, "identityId is required");
        Date now = new Date();
        return refreshTokenRepository.findActiveByIdentityId(identityId)
                .map(tokens -> tokens.stream()
                        .filter(t -> t.getExpiresAt().after(now))
                        .map(t -> new DelegateSession(t.getFamilyId(), t.getLabel(),
                                                      t.getCreated(), t.getExpiresAt()))
                        .toList());
    }

    @Override
    public Future<Void> revokeFamily(String identityId, String familyId) {
        Validate.notBlank(identityId, "identityId is required");
        Validate.notBlank(familyId, "familyId is required");
        return refreshTokenRepository.revokeFamily(identityId, familyId);
    }

    @Override
    public Future<Void> revokeAllFor(String identityId) {
        Validate.notBlank(identityId, "identityId is required");
        return refreshTokenRepository.revokeAllFor(identityId);
    }

    @Override
    public Future<RefreshTokenRotation> rotate(String token) {
        Validate.notBlank(token, "token is required");
        return refreshTokenRepository.findByTokenHash(DomainUtil.sha256Hex(token))
                                     .compose(this::rotateExisting);
    }

    private Future<RefreshTokenRotation> rotateExisting(RefreshToken current) {
        if (current == null) {
            return Future.failedFuture(new IllegalArgumentException("Unknown refresh token"));
        }

        if (current.isRevoked()) {
            return reused(current);
        }

        if (current.getExpiresAt().before(new Date())) {
            return Future.failedFuture(new IllegalArgumentException("Refresh token has expired"));
        }

        if (current.getAudience() == null) {
            // mint() stamps every lineage, so this is a corrupted record; failing beats guessing an
            // audience, which would hand the replacement a surface the lineage never covered
            log.error("Refresh token {} in family {} has no audience", current.getId(), current.getFamilyId());
            return Future.failedFuture(new IllegalStateException("Refresh token has no audience"));
        }

        return identityRepository.findById(current.getIdentityId())
                .compose(identity -> {
                    if (identity == null || !identity.isEnabled()) {
                        return Future.failedFuture(
                                new IllegalArgumentException("Refresh token identity is missing or disabled"));
                    }

                    // The presented token is consumed before the replacement is minted, under the replacement's
                    // id, so two rotations presenting one token at once mint at most one replacement: the one
                    // that finds the token consumed is a reuse. A consumed token whose replacement then fails to
                    // mint sends the client back to sign in, which beats a lineage holding two live tokens.
                    String replacementId = UUID.randomUUID().toString();
                    return refreshTokenRepository.consume(current.getId(), replacementId)
                            .compose(consumed -> consumed
                                    ? mint(replacementId, current.getIdentityId(), current.getFamilyId(), current.getAudience(),
                                           current.getLabel())
                                            .map(plaintext -> new RefreshTokenRotation(identity, plaintext, current.getAudience()))
                                    : reused(current));
                });
    }

    // Reuse of an already-rotated token — the lineage is compromised; revoke it entirely. The
    // family's rows all carry the identity the lineage was issued to, so the scoped revocation
    // reaches every one of them
    private Future<RefreshTokenRotation> reused(RefreshToken current) {
        log.warn("Refresh token reuse detected for family {}; revoking the whole family", current.getFamilyId());
        return refreshTokenRepository.revokeFamily(current.getIdentityId(), current.getFamilyId())
                .compose(v -> Future.failedFuture(new IllegalArgumentException("Refresh token reuse detected")));
    }

    // the plaintext exists only here and on its way to the client; the record holds its hash
    private Future<String> mint(String id, String identityId, String familyId, KinoticAudience audience, String label) {
        String plaintext = DomainUtil.generateUrlSafeToken(TOKEN_BYTES);
        Date now = new Date();
        RefreshToken record = new RefreshToken()
                .setId(id)
                .setTokenHash(DomainUtil.sha256Hex(plaintext))
                .setIdentityId(identityId)
                .setFamilyId(familyId)
                .setLabel(label)
                .setAudience(audience)
                .setCreated(now)
                .setExpiresAt(new Date(now.getTime() + TOKEN_TTL_MS))
                .setRevoked(false);
        return refreshTokenRepository.saveSync(record)
                                     .map(plaintext);
    }
}
