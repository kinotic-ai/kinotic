package org.kinotic.domain.api.model.iam;

/**
 * Result of rotating a refresh token: the owning user and the replacement token.
 */
public record RefreshTokenRotation(IamUser user, IssuedRefreshToken issued) {}
