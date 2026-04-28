package org.kinotic.os.github.internal.api.services;

import io.vertx.core.json.JsonObject;
import lombok.RequiredArgsConstructor;
import org.kinotic.os.github.api.model.GitHubAppInstallation;
import org.kinotic.os.internal.api.services.CrudServiceTemplate;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.concurrent.CompletableFuture;

/**
 * Internal persistence shim used by the gateway's install-callback handler. The
 * callback runs without an authenticated Kinotic Participant (it's a top-level browser
 * redirect from GitHub), so the org-scoped CRUD service can't be used; instead we
 * persist directly via {@link CrudServiceTemplate} with the orgId carried over from
 * the install-start session.
 */
@Component
@RequiredArgsConstructor
public class GitHubAppInstallationStore {

    private static final String INDEX = "kinotic_github_app_installation";

    private final CrudServiceTemplate crudServiceTemplate;

    /**
     * Builds a {@link GitHubAppInstallation} from the GitHub installation JSON and
     * persists it under the given org. Idempotent — repeated calls overwrite.
     */
    public CompletableFuture<GitHubAppInstallation> upsertFromGithub(String orgId,
                                                                     String installationId,
                                                                     JsonObject githubJson) {
        JsonObject account = githubJson.getJsonObject("account");
        Date now = new Date();
        GitHubAppInstallation install = new GitHubAppInstallation()
                .setId(installationId)
                .setOrganizationId(orgId)
                .setGithubInstallationId(installationId)
                .setAccountLogin(account != null ? account.getString("login") : null)
                .setAccountType(account != null ? account.getString("type") : null)
                .setCreated(now)
                .setUpdated(now);
        return crudServiceTemplate
                .save(INDEX, install.getId(), install, b -> b.routing(orgId))
                .thenApply(r -> install);
    }
}
