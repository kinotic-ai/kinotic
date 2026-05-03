package org.kinotic.github.api.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Result of finalising a GitHub install round-trip. Returned to the SPA's callback
 * component so it can drive the post-install UX (e.g. re-opening the new-project
 * sidebar). {@code intent} and {@code returnTo} echo what the SPA staged when it
 * called {@code startInstall(...)}; both are {@code null} for installs started
 * without an intent.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class GitHubInstallCompletion {

    /** The persisted installation row. */
    private GitHubAppInstallation installation;

    /** Free-form intent string the SPA originally staged (e.g. {@code "openNewProject"}). */
    private String intent;

    /** SPA route the user wanted to land back on. */
    private String returnTo;
}
