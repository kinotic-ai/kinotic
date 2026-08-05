package org.kinotic.domain.internal.api.services.iam;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.kinotic.domain.api.model.iam.KinoticAudience;
import org.kinotic.domain.api.model.iam.DelegateSession;
import org.kinotic.domain.api.model.iam.RefreshTokenRotation;
import org.kinotic.domain.api.services.iam.RefreshTokenService;
import org.kinotic.domain.internal.api.model.RefreshToken;
import org.kinotic.domain.internal.api.repositories.ParticipantIdentityRepository;
import org.kinotic.domain.internal.api.repositories.RefreshTokenRepository;
import org.kinotic.domain.api.utils.DomainUtil;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

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
    public CompletableFuture<String> issue(String identityId, KinoticAudience audience, String label) {
        Validate.notBlank(identityId, "identityId is required");
        Validate.notNull(audience, "audience is required");
        return mint(identityId, UUID.randomUUID().toString(), audience, StringUtils.trimToNull(label))
                .thenApply(Minted::plaintext);
    }

    @Override
    public CompletableFuture<List<DelegateSession>> findActiveSessions(String identityId) {
        Validate.notBlank(identityId, "identityId is required");
        Date now = new Date();
        return refreshTokenRepository.findActiveByIdentityId(identityId)
                .thenApply(tokens -> tokens.stream()
                        .filter(t -> t.getExpiresAt().after(now))
                        .map(t -> new DelegateSession(t.getFamilyId(), t.getLabel(),
                                                      t.getCreated(), t.getExpiresAt()))
                        .toList());
    }

    @Override
    public CompletableFuture<Void> revokeFamily(String identityId, String familyId) {
        Validate.notBlank(identityId, "identityId is required");
        Validate.notBlank(familyId, "familyId is required");
        return refreshTokenRepository.findByFamilyId(familyId)
                .thenCompose(tokens -> {
                    // identity scoping over every row: a family id from another identity's
                    // lineage revokes nothing
                    CompletableFuture<?>[] saves = tokens.stream()
                            .filter(t -> identityId.equals(t.getIdentityId()) && !t.isRevoked())
                            .map(t -> refreshTokenRepository.saveSync(t.setRevoked(true)))
                            .toArray(CompletableFuture[]::new);
                    return CompletableFuture.allOf(saves);
                });
    }

    @Override
    public CompletableFuture<Void> revokeAllFor(String identityId) {
        Validate.notBlank(identityId, "identityId is required");
        return refreshTokenRepository.findActiveByIdentityId(identityId)
                .thenCompose(tokens -> {
                    CompletableFuture<?>[] saves = tokens.stream()
                            .map(t -> refreshTokenRepository.saveSync(t.setRevoked(true)))
                            .toArray(CompletableFuture[]::new);
                    return CompletableFuture.allOf(saves);
                });
    }

    @Override
    public CompletableFuture<RefreshTokenRotation> rotate(String token) {
        Validate.notBlank(token, "token is required");
        return refreshTokenRepository.findByTokenHash(DomainUtil.sha256Hex(token))
                                     .thenCompose(this::rotateExisting);
    }

    private CompletableFuture<RefreshTokenRotation> rotateExisting(RefreshToken current) {
        if (current == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Unknown refresh token"));
        }
        if (current.isRevoked()) {
            // Reuse of an already-rotated token — the lineage is compromised; revoke it entirely.
            log.warn("Refresh token reuse detected for family {}; revoking the whole family", current.getFamilyId());
            return revokeFamily(current.getFamilyId())
                    .thenCompose(v -> CompletableFuture.failedFuture(
                            new IllegalArgumentException("Refresh token reuse detected")));
        }
        if (current.getExpiresAt().before(new Date())) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Refresh token has expired"));
        }
        if (current.getAudience() == null) {
            // mint() stamps every lineage, so this is a corrupted record; failing beats guessing an
            // audience, which would hand the replacement a surface the lineage never covered
            log.error("Refresh token {} in family {} has no audience", current.getId(), current.getFamilyId());
            return CompletableFuture.failedFuture(new IllegalStateException("Refresh token has no audience"));
        }
        return identityRepository.findById(current.getIdentityId())
                .thenCompose(identity -> {
                    if (identity == null || !identity.isEnabled()) {
                        return CompletableFuture.failedFuture(
                                new IllegalArgumentException("Refresh token identity is missing or disabled"));
                    }
                    // Mint the replacement before revoking the current token so a failure mid-rotation
                    // never leaves the client without a usable token.
                    return mint(current.getIdentityId(), current.getFamilyId(), current.getAudience(),
                                current.getLabel())
                            .thenCompose(minted -> {
                                current.setRevoked(true)
                                       .setLastUsedAt(new Date())
                                       .setReplacedById(minted.record().getId());
                                return refreshTokenRepository.saveSync(current)
                                        .thenApply(v -> new RefreshTokenRotation(identity, minted.plaintext(),
                                                                                  current.getAudience()));
                            });
                });
    }

    private CompletableFuture<Void> revokeFamily(String familyId) {
        return refreshTokenRepository.findByFamilyId(familyId)
                .thenCompose(tokens -> {
                    CompletableFuture<?>[] saves = tokens.stream()
                            .filter(t -> !t.isRevoked())
                            .map(t -> refreshTokenRepository.saveSync(t.setRevoked(true)))
                            .toArray(CompletableFuture[]::new);
                    return CompletableFuture.allOf(saves);
                });
    }

    private CompletableFuture<Minted> mint(String identityId, String familyId, KinoticAudience audience, String label) {
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
        return refreshTokenRepository.saveSync(record).thenApply(saved -> new Minted(record, plaintext));
    }

    /** A persisted {@link RefreshToken} paired with its plaintext, which exists only at mint time. */
    private record Minted(RefreshToken record, String plaintext) {}
}
