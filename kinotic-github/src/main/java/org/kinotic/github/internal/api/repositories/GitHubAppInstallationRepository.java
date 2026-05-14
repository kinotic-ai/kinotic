package org.kinotic.github.internal.api.repositories;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.internal.api.repositories.AbstractRepository;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.kinotic.github.api.model.GitHubAppInstallation;
import org.springframework.stereotype.Repository;

import java.util.concurrent.CompletableFuture;

@Repository
public class GitHubAppInstallationRepository extends AbstractRepository<GitHubAppInstallation> {

    public GitHubAppInstallationRepository(ElasticsearchAsyncClient esAsyncClient,
                                           CrudServiceTemplate crudServiceTemplate) {
        super("kinotic_github_app_installation", GitHubAppInstallation.class, esAsyncClient, crudServiceTemplate);
    }

    /**
     * Finds the first installation matching the given GitHub installation id with optional routing
     * and an optional extra filter (typically an org filter supplied by the service tier).
     */
    public CompletableFuture<GitHubAppInstallation> findByGithubInstallationId(long githubInstallationId,
                                                                               String routing,
                                                                               Query extraFilter) {
        Query query = Query.of(qb -> qb.bool(b -> {
            b.filter(f -> f.term(t -> t.field("githubInstallationId").value(githubInstallationId)));
            if (extraFilter != null) {
                b.filter(extraFilter);
            }
            return b;
        }));
        return crudServiceTemplate.search(indexName, Pageable.ofSize(1), type, b -> {
            if (routing != null) b.routing(routing);
            b.query(query);
        }).thenApply(page -> page.getContent().isEmpty() ? null : page.getContent().getFirst());
    }
}
