package org.kinotic.domain.api.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

import java.time.Instant;

/**
 * Short-lived credential for cloning a {@link Project}'s backing repository, bundled with
 * the clone metadata a worker needs to {@code git fetch} it.
 */
@Getter
@RequiredArgsConstructor
@ToString(onlyExplicitlyIncluded = true)
public class ProjectRepoToken {

    /** The token authorizing read access to the repository. Never logged. */
    private final String token;

    /** When the token stops working. */
    @ToString.Include
    private final Instant expiresAt;

    /** {@code https://github.com/<owner>/<repo>.git} for the project's repo. */
    @ToString.Include
    private final String cloneUrl;

    /** Default branch on the repo (e.g. {@code main}). */
    @ToString.Include
    private final String defaultBranch;

}
