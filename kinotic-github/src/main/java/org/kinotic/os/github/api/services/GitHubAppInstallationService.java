package org.kinotic.os.github.api.services;

import org.kinotic.core.api.annotations.Publish;
import org.kinotic.core.api.crud.IdentifiableCrudService;
import org.kinotic.os.github.api.model.GitHubAppInstallation;

import java.util.concurrent.CompletableFuture;

/**
 * Service the frontend uses to read GitHub-link state and unlink an installation. The
 * link itself is created by the gateway's install-callback REST handler — that flow
 * needs the Vert.x session context, so it isn't a service method.
 * <p>
 * Org-scoped via {@code OrganizationScoped} on {@link GitHubAppInstallation}.
 */
@Publish
public interface GitHubAppInstallationService extends IdentifiableCrudService<GitHubAppInstallation, String> {

    /**
     * Returns the (at-most-one) installation bound to the caller's organization, or
     * {@code null} if GitHub is not yet linked. Drives the "linked / not linked"
     * indicator in the org-settings UI.
     */
    CompletableFuture<GitHubAppInstallation> findForCurrentOrg();
}
