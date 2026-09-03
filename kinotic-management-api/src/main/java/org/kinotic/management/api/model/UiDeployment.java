package org.kinotic.management.api.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.core.api.crud.Identifiable;

import java.util.Date;

/**
 * The standing deployment of one UI artifact of a {@link Project}: the site serving it, the
 * commit it serves, and its status. One row per UI a deployment has published; a row outlives
 * the artifact until the deployment is removed.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class UiDeployment implements Identifiable<String> {

    /**
     * The site's hostname label under the platform's sites domain, minted once when the UI
     * is first published and never parsed: {@code <org>-<app>-<ui>}, with a numeric suffix
     * when that label is taken. The site's resources are named after it.
     */
    private String id;

    private String organizationId;

    private String applicationId;

    /**
     * The id of the project the UI belongs to.
     */
    private String projectId;

    /**
     * The UI's identity: the {@link UiArtifact#name()} it was published from. Unique among
     * the project's UI deployments.
     */
    private String name;

    /**
     * Where the site is served, {@code https://<id>.<sites domain>}, fixed when the label is
     * minted.
     */
    private String url;

    /**
     * Sha of the commit the site serves, or {@code null} until the first publish completes.
     */
    private String commitSha;

    /**
     * Sha of the commit the site served before {@link #commitSha}, whose files are kept so
     * tabs opened on it keep working, or {@code null} when there is none.
     */
    private String previousCommitSha;

    private UiDeploymentStatus status;

    private Date created;

    private Date updated;

}
