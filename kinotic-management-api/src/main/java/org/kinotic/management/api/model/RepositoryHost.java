package org.kinotic.management.api.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.management.api.config.RepoHostType;

/**
 * Where projects' repositories are hosted.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class RepositoryHost {

    /** The host of every project's repository. */
    private RepoHostType type;

    /**
     * The base URL of the host's web pages as a person's browser reaches it (no trailing slash): a repository's page is
     * this URL followed by {@code /} and its {@code repoFullName}.
     */
    private String webBaseUrl;

}
