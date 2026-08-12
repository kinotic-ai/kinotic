package org.kinotic.github.api.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Result of finalising a GitHub install round-trip. Returned to the SPA's callback
 * component so it can drive the post-install UX. {@code returnTo} echoes what the
 * SPA staged when it called {@code startInstall(...)} and may carry query params
 * (e.g. {@code /projects?openNewProject=1}) so the destination page can pick up
 * where the user left off.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class GitHubInstallCompletion {

    /** The persisted installation row. */
    private GitHubAppInstallation installation;

    /** SPA route the user wanted to land back on. */
    private String returnTo;
}
