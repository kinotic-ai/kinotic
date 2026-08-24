package org.kinotic.github.api.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

import java.time.Instant;

/**
 * A short-lived GitHub installation access token. Immutable — once minted, the
 * value pair {@code (token, expiresAt)} is fixed. {@code expiresAt} is GitHub's
 * authoritative wall-clock expiry; callers must not use the token past that point.
 */
@Getter
// The token is a live credential, so it is kept out of toString to stop it reaching
// logs through a "{}" format argument or an interpolated exception message.
@ToString(onlyExplicitlyIncluded = true)
@AllArgsConstructor
public class GitHubToken {

    /** Bearer token to send as {@code Authorization: Bearer <token>}. */
    private final String token;

    /** Absolute UTC instant at which GitHub will reject this token. */
    @ToString.Include
    private final Instant expiresAt;
}
