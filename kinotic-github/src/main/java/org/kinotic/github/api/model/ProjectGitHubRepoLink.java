package org.kinotic.github.api.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.os.api.model.OrganizationScoped;

import java.util.Date;

/**
 * Persisted link between a Kinotic Project and a single GitHub repository reachable
 * through an existing {@link GitHubAppInstallation}. Drives webhook dispatch (delivery
 * → project) and ref-creation auth (project → which repo, via which installation).
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class ProjectGitHubRepoLink implements OrganizationScoped<String> {

    /** Stable Kinotic id; defaults to the projectId since each project has at most one link. */
    private String id;

    private String projectId;

    private String organizationId;

    /** Kinotic id of the {@link GitHubAppInstallation} this link routes through. */
    private String installationId;

    /** {@code owner/repo} string from GitHub. */
    private String repoFullName;

    /** GitHub's repo id (long, stored as string). Stable across renames. */
    private String repoId;

    private String defaultBranch;

    private Date updated;
}
