package org.kinotic.github.internal.api.repositories;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import org.apache.commons.lang3.Validate;
import org.kinotic.domain.internal.api.repositories.AbstractOrganizationScopedRepository;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.kinotic.github.api.model.GitHubAppInstallation;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
public class GitHubAppInstallationRepository extends AbstractOrganizationScopedRepository<GitHubAppInstallation> {

    public GitHubAppInstallationRepository(ElasticsearchAsyncClient esAsyncClient,
                                           CrudServiceTemplate crudServiceTemplate) {
        super("kinotic_github_app_installation", GitHubAppInstallation.class, esAsyncClient, crudServiceTemplate);
    }

    public CompletableFuture<GitHubAppInstallation> findByGithubInstallationId(long githubInstallationId) {
        return findFirst(b -> b.query(termFilter("githubInstallationId", githubInstallationId)));
    }

    public CompletableFuture<GitHubAppInstallation> findByGithubInstallationId(long githubInstallationId, String orgId) {
        Validate.notBlank(orgId, "orgId cannot be blank");
        return findFirst(b -> b.routing(orgId)
                .query(composeOrgFilter(orgId, termFilter("githubInstallationId", githubInstallationId))));
    }
}
