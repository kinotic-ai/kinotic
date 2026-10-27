package org.kinotic.management.api.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;

/**
 * Short-lived credential for a {@link Project}'s backing repository, bundled with the clone
 * metadata a worker needs to {@code git fetch} it.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(onlyExplicitlyIncluded = true)
public class ProjectRepoToken {

    /**
     * The token authorizing access to the repository, presented to Git over HTTP as the password of the user
     * {@code x-access-token}. Never logged.
     */
    private String token;

    /** When the token stops working. */
    @ToString.Include
    private Instant expiresAt;

    /** The HTTP clone URL of the project's repository, e.g. {@code https://github.com/<owner>/<repo>.git}. */
    @ToString.Include
    private String cloneUrl;

    /** Default branch on the repo (e.g. {@code main}). */
    @ToString.Include
    private String defaultBranch;

}
