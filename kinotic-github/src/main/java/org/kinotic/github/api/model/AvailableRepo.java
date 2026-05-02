package org.kinotic.github.api.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * One row in the repo dropdown shown when linking a project. Carries just enough info
 * for the UI; a successful link round-trips through {@code ProjectGitHubRepoService}
 * which re-validates against GitHub before persisting.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class AvailableRepo {
    private Long repoId;
    private String fullName;
    private String defaultBranch;
    private boolean privateRepo;
}
