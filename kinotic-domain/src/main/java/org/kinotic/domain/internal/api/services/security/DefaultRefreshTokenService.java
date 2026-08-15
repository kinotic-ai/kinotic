package org.kinotic.domain.internal.api.services.security;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.kinotic.domain.api.model.security.KinoticAudience;
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
import java.util.function.Predicate;

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
        return mint(identityId, UUID.randomUUID().toString(), audience, StringUtils.trimToNull(label))
                .map(Minted::plaintext);
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
        // identity scoping over every row: a family id from another identity's
        // lineage revokes nothing
        return revokeMatching(refreshTokenRepository.findByFamilyId(familyId),
                              t -> identityId.equals(t.getIdentityId()));
    }

    @Override
    public Future<Void> revokeAllFor(String identityId) {
        Validate.notBlank(identityId, "identityId is required");
        return revokeMatching(refreshTokenRepository.findActiveByIdentityId(identityId),
                              t -> true);
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
            // Reuse of an already-rotated token — the lineage is compromised; revoke it entirely.
            log.warn("Refresh token reuse detected for family {}; revoking the whole family", current.getFamilyId());
            return revokeFamily(current.getFamilyId())
                    .compose(v -> Future.failedFuture(
                            new IllegalArgumentException("Refresh token reuse detected")));
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
                    // Mint the replacement before revoking the current token so a failure mid-rotation
                    // never leaves the client without a usable token.
                    return mint(current.getIdentityId(), current.getFamilyId(), current.getAudience(),
                                current.getLabel())
                            .compose(minted -> {
                                current.setRevoked(true)
                                       .setLastUsedAt(new Date())
                                       .setReplacedById(minted.record().getId());
                                return refreshTokenRepository.saveSync(current)
                                        .map(new RefreshTokenRotation(identity, minted.plaintext(),
                                                                      current.getAudience()));
                            });
                });
    }

    // unscoped by design: reuse detection already proved the lineage compromised, so the
    // whole family dies regardless of which identity its rows carry
    private Future<Void> revokeFamily(String familyId) {
        return revokeMatching(refreshTokenRepository.findByFamilyId(familyId),
                              t -> true);
    }

    /**
     * Marks every not-yet-revoked token in {@code tokens} that matches {@code scope} as
     * revoked. The scope predicate is the security-relevant part of every revocation — each
     * caller states its own.
     */
    private Future<Void> revokeMatching(Future<List<RefreshToken>> tokens,
                                        Predicate<RefreshToken> scope) {
        return tokens.compose(list -> {
            List<Future<RefreshToken>> saves = list.stream()
                    .filter(t -> !t.isRevoked() && scope.test(t))
                    .map(t -> refreshTokenRepository.saveSync(t.setRevoked(true)))
                    .toList();
            return Future.all(saves).mapEmpty();
        });
    }

    private Future<Minted> mint(String identityId, String familyId, KinoticAudience audience, String label) {
        String plaintext = DomainUtil.generateUrlSafeToken(TOKEN_BYTES);
        Date now = new Date();
        RefreshToken record = new RefreshToken()
                .setId(UUID.randomUUID().toString())
                .setTokenHash(DomainUtil.sha256Hex(plaintext))
                .setIdentityId(identityId)
                .setFamilyId(familyId)
                .setLabel(label)
                .setAudience(audience)
                .setCreated(now)
                .setExpiresAt(new Date(now.getTime() + TOKEN_TTL_MS))
                .setRevoked(false);
        return refreshTokenRepository.saveSync(record)
                                     .map(new Minted(record, plaintext));
    }

    /** A persisted {@link RefreshToken} paired with its plaintext, which exists only at mint time. */
    private record Minted(RefreshToken record, String plaintext) {}
}
