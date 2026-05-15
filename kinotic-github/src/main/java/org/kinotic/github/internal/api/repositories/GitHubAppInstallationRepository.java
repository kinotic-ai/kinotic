package org.kinotic.github.internal.api.repositories;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.internal.api.repositories.AbstractOrganizationScopedRepository;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.kinotic.github.api.model.GitHubAppInstallation;
import org.springframework.stereotype.Repository;

import java.util.concurrent.CompletableFuture;

@Repository
public class GitHubAppInstallationRepository extends AbstractOrganizationScopedRepository<GitHubAppInstallation> {

    public GitHubAppInstallationRepository(ElasticsearchAsyncClient esAsyncClient,
                                           CrudServiceTemplate crudServiceTemplate) {
        super("kinotic_github_app_installation", GitHubAppInstallation.class, esAsyncClient, crudServiceTemplate);
    }

    public CompletableFuture<GitHubAppInstallation> findByGithubInstallationId(long githubInstallationId) {
        return findAll(Pageable.ofSize(1),
                       b -> b.query(composeFilter(githubInstallationIdFilter(githubInstallationId))))
                .thenApply(page -> page.getContent().isEmpty() ? null : page.getContent().getFirst());
    }

    public CompletableFuture<GitHubAppInstallation> findByGithubInstallationId(long githubInstallationId, String orgId) {
        Validate.notBlank(orgId, "orgId cannot be blank");
        return findAll(Pageable.ofSize(1),
                       b -> b.routing(orgId).query(composeOrgFilter(orgId, githubInstallationIdFilter(githubInstallationId))))
                .thenApply(page -> page.getContent().isEmpty() ? null : page.getContent().getFirst());
    }

    private Query githubInstallationIdFilter(long githubInstallationId) {
        return TermQuery.of(t -> t.field("githubInstallationId").value(githubInstallationId))._toQuery();
    }
}
