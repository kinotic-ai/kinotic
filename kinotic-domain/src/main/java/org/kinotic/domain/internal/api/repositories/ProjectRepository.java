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
public class ProjectRepository extends AbstractApplicationRepository<Project> {

    public ProjectRepository(ElasticsearchAsyncClient esAsyncClient,
                             CrudServiceTemplate crudServiceTemplate) {
        super("kinotic_project", Project.class, esAsyncClient, crudServiceTemplate);
    }

    /**
     * Finds projects by their backing repository's full name, optionally routed and
     * constrained by an extra filter (typically the org filter supplied by the service).
     */
    public CompletableFuture<List<Project>> findByRepoFullName(String repoFullName, String routing, Query extraFilter) {
        Query query = Query.of(qb -> qb.bool(b -> {
            b.filter(TermQuery.of(t -> t.field("repoFullName").value(repoFullName))._toQuery());
            if (extraFilter != null) {
                b.filter(extraFilter);
            }
            return b;
        }));
        return crudServiceTemplate.search(indexName, Pageable.ofSize(50), type, b -> {
            if (routing != null) b.routing(routing);
            b.query(query);
        }).thenApply(Page::getContent);
    }
}
