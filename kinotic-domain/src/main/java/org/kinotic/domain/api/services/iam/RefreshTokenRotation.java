package org.kinotic.domain.api.services.iam;

import org.kinotic.domain.api.model.iam.IamUser;

/**
 * Result of rotating a refresh token: the owning user and the replacement token.
 */
public record RefreshTokenRotation(IamUser user, IssuedRefreshToken issued) {}
