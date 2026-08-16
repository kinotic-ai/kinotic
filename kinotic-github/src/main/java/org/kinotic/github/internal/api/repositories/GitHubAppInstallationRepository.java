package org.kinotic.github.internal.api.repositories;

import io.vertx.core.Future;
import org.kinotic.domain.internal.api.repositories.AbstractOrganizationScopedRepository;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.kinotic.github.api.model.GitHubAppInstallation;
import org.springframework.stereotype.Component;

@Component
public class GitHubAppInstallationRepository extends AbstractOrganizationScopedRepository<GitHubAppInstallation> {

    public GitHubAppInstallationRepository(CrudServiceTemplate crudServiceTemplate) {
        super("kinotic_github_app_installation", GitHubAppInstallation.class, crudServiceTemplate);
    }

    public Future<GitHubAppInstallation> findByGithubInstallationId(long githubInstallationId) {
        return findFirst(b -> b.query(termFilter("githubInstallationId", githubInstallationId)));
    }
}
