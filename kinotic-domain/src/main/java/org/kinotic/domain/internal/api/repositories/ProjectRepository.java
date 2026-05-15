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
import java.util.concurrent.CompletableFuture;

@Repository
public class ProjectRepository extends AbstractApplicationScopedRepository<Project> {

    public ProjectRepository(ElasticsearchAsyncClient esAsyncClient,
                             CrudServiceTemplate crudServiceTemplate) {
        super("kinotic_project", Project.class, esAsyncClient, crudServiceTemplate);
    }

    public CompletableFuture<List<Project>> findByRepoFullName(String repoFullName) {
        return findByRepoFullName(repoFullName, null);
    }

    public CompletableFuture<List<Project>> findByRepoFullName(String repoFullName, String orgId) {
        Query query = composeOrgFilter(orgId,
                                       TermQuery.of(t -> t.field("repoFullName").value(repoFullName))._toQuery());
        return doSearch(Pageable.ofSize(50), b -> {
            if (orgId != null) b.routing(orgId);
            b.query(query);
        }).thenApply(Page::getContent);
    }
}
