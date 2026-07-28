package org.kinotic.domain.api.model.iam;

/**
 * Result of rotating a refresh token: the owning user and the plaintext replacement token,
 * available only here since the server stores only its hash.
 */
public record RefreshTokenRotation(IamUser user, String refreshToken) {}
