package org.kinotic.os.github.api.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.time.Instant;

/**
 * What worker nodes receive when they ask for clone credentials. The token is a
 * short-lived GitHub installation access token scoped to a single repository with
 * {@code contents:read} permission; {@code expiresAt} is the absolute UTC instant.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class IssuedInstallationToken {
    /** Bearer token to send as {@code Authorization: Bearer <token>} on git over HTTPS. */
    private String token;

    /** Absolute expiry. Workers should not use the token beyond this point. */
    private Instant expiresAt;

    /** {@code https://github.com/<owner>/<repo>.git} for the linked repo. */
    private String cloneUrl;

    /** Default branch on the linked repo (e.g. {@code main}). */
    private String defaultBranch;
}
