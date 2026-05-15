package org.kinotic.domain.internal.api.repositories;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.Project;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

@Repository
public class ProjectRepository extends AbstractApplicationScopedRepository<Project> {

    public ProjectRepository(ElasticsearchAsyncClient esAsyncClient,
                             CrudServiceTemplate crudServiceTemplate) {
        super("kinotic_project", Project.class, esAsyncClient, crudServiceTemplate);
    }

    public CompletableFuture<List<Project>> findByRepoFullName(String repoFullName) {
        return findAll(Pageable.ofSize(50),
                       b -> b.query(composeOrgFilter(null, repoFullNameFilter(repoFullName))))
                .thenApply(Page::getContent);
    }

    public CompletableFuture<List<Project>> findByRepoFullName(String repoFullName, String orgId) {
        Objects.requireNonNull(orgId, "orgId");
        return findAll(Pageable.ofSize(50),
                       b -> b.routing(orgId).query(composeOrgFilter(orgId, repoFullNameFilter(repoFullName))))
                .thenApply(Page::getContent);
    }

    private Query repoFullNameFilter(String repoFullName) {
        return TermQuery.of(t -> t.field("repoFullName").value(repoFullName))._toQuery();
    }
}
