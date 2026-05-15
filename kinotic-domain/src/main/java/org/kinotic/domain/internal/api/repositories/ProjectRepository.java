package org.kinotic.domain.internal.api.repositories;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.Project;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

@Repository
public class ProjectRepository extends AbstractApplicationRepository<Project> {

    public ProjectRepository(ElasticsearchAsyncClient esAsyncClient,
                             CrudServiceTemplate crudServiceTemplate) {
        super("kinotic_project", Project.class, esAsyncClient, crudServiceTemplate);
    }

    public CompletableFuture<List<Project>> findByRepoFullName(String repoFullName) {
        return findByRepoFullName(repoFullName, null);
    }

    /**
     * Finds projects by their backing repository's full name. The repository sets a default
     * query filtering by {@code repoFullName}; the consumer fires afterward and may augment
     * routing or install a composed query via {@link #buildRepoFullNameQuery(String, Query)}.
     */
    public CompletableFuture<List<Project>> findByRepoFullName(String repoFullName, Consumer<SearchRequest.Builder> builderConsumer) {
        Query baseQuery = buildRepoFullNameQuery(repoFullName, null);
        return crudServiceTemplate.search(indexName, Pageable.ofSize(50), type, b -> {
            b.query(baseQuery);
            if (builderConsumer != null) builderConsumer.accept(b);
        }).thenApply(Page::getContent);
    }

    /**
     * Builds a bool query whose {@code filter} clauses include the {@code repoFullName} term
     * and, when supplied, an additional caller-provided filter.
     */
    public Query buildRepoFullNameQuery(String repoFullName, Query extraFilter) {
        return Query.of(qb -> qb.bool(b -> {
            b.filter(TermQuery.of(t -> t.field("repoFullName").value(repoFullName))._toQuery());
            if (extraFilter != null) {
                b.filter(extraFilter);
            }
            return b;
        }));
    }
}
