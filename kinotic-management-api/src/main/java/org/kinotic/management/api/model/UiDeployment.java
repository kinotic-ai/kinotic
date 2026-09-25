package org.kinotic.management.api.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.domain.api.reconcile.Reconcilable;
import org.kinotic.domain.api.reconcile.ReconcileState;
import org.kinotic.domain.api.model.DeploymentState;
import org.kinotic.domain.api.model.OrganizationScoped;

import java.util.Date;

/**
 * The standing deployment of one UI artifact of a {@link Project}: the site serving it, and
 * what the deployment should be beside what it is. One row per UI a deployment has published;
 * a row outlives the artifact until the deployment is removed.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class UiDeployment implements Reconcilable<DeploymentState>, OrganizationScoped<String> {

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
     * Why the site does not yet serve what it should, as last observed, or {@code null} when it
     * does.
     */
    private String failureMessage;

    /**
     * What the deployment should be, the commit its project's last deployment published to the
     * site, beside what it is, the phase it is in and the commit the site serves, with what the
     * platform keeps on every watched record: the project deployment it belongs to.
     */
    private ReconcileState<DeploymentState> state = new ReconcileState<>();

    private Date created;

    private Date updated;
}
