package org.kinotic.domain.api.model.security;

import org.kinotic.domain.api.model.security.identity.ParticipantIdentity;

/**
 * Result of rotating a refresh token: the identity the lineage authenticates, the plaintext
 * replacement token (available only here — the server stores only its hash), and the audience
 * the lineage was issued for, which the replacement access token must be minted with.
 */
public record RefreshTokenRotation(ParticipantIdentity identity, String refreshToken, KinoticAudience audience) {}
