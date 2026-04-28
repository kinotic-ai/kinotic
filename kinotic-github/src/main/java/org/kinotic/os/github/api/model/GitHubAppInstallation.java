package org.kinotic.os.github.api.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.os.api.model.OrganizationScoped;

import java.util.Date;

/**
 * Persisted record of one GitHub App installation that a Kinotic Org has authorised.
 * The durable binding that says "Org X has access to GitHub install Y" — without it,
 * no installation token can be minted on behalf of the org and webhook deliveries
 * can't be matched to a project.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class GitHubAppInstallation implements OrganizationScoped<String> {

    /** Stable Kinotic id; defaults to the GitHub installation id as a string. */
    private String id;

    private String organizationId;

    /** GitHub's installation id (long, stored as string for index uniformity). */
    private String githubInstallationId;

    /** GitHub login of the user or org that the app is installed under. */
    private String accountLogin;

    /** Either {@code User} or {@code Organization} per GitHub's API. */
    private String accountType;

    /** Set when the installation is suspended; null otherwise. */
    private Date suspendedAt;

    private Date created;
    private Date updated;
}
