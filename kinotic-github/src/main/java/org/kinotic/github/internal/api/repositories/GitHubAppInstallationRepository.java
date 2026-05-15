package org.kinotic.github.internal.api.repositories;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.internal.api.repositories.AbstractRepository;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.kinotic.github.api.model.GitHubAppInstallation;
import org.springframework.stereotype.Repository;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

@Repository
public class GitHubAppInstallationRepository extends AbstractRepository<GitHubAppInstallation> {

    public GitHubAppInstallationRepository(ElasticsearchAsyncClient esAsyncClient,
                                           CrudServiceTemplate crudServiceTemplate) {
        super("kinotic_github_app_installation", GitHubAppInstallation.class, esAsyncClient, crudServiceTemplate);
    }

    public CompletableFuture<GitHubAppInstallation> findByGithubInstallationId(long githubInstallationId) {
        return findByGithubInstallationId(githubInstallationId, null);
    }

    /**
     * Finds the first installation matching the given GitHub installation id. The repository
     * sets a default query filtering by {@code githubInstallationId}; the consumer fires
     * afterward and may augment routing or install a composed query via
     * {@link #buildGithubInstallationIdQuery(long, Query)}.
     */
    public CompletableFuture<GitHubAppInstallation> findByGithubInstallationId(long githubInstallationId,
                                                                               Consumer<SearchRequest.Builder> builderConsumer) {
        Query baseQuery = buildGithubInstallationIdQuery(githubInstallationId, null);
        return crudServiceTemplate.search(indexName, Pageable.ofSize(1), type, b -> {
            b.query(baseQuery);
            if (builderConsumer != null) builderConsumer.accept(b);
        }).thenApply(page -> page.getContent().isEmpty() ? null : page.getContent().getFirst());
    }

    /**
     * Builds a bool query whose {@code filter} clauses include the {@code githubInstallationId}
     * term and, when supplied, an additional caller-provided filter.
     */
    public Query buildGithubInstallationIdQuery(long githubInstallationId, Query extraFilter) {
        return Query.of(qb -> qb.bool(b -> {
            b.filter(f -> f.term(t -> t.field("githubInstallationId").value(githubInstallationId)));
            if (extraFilter != null) {
                b.filter(extraFilter);
            }
            return b;
        }));
    }
}
