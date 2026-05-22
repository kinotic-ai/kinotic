package org.kinotic.domain.api.model.iam;

import java.util.Date;

/**
 * A newly issued refresh token. The plaintext {@link #token()} is available only here.
 */
public record IssuedRefreshToken(String token, Date expiresAt) {}
