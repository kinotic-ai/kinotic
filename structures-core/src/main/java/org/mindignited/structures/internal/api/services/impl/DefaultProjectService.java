package org.mindignited.structures.internal.api.services.impl;

import java.util.Date;
import java.util.concurrent.CompletableFuture;

import org.apache.commons.lang3.Validate;
import org.kinotic.continuum.core.api.crud.Page;
import org.kinotic.continuum.core.api.crud.Pageable;
import org.mindignited.structures.api.domain.Project;
import org.mindignited.structures.api.services.ProjectService;
import org.mindignited.structures.api.services.StructureService;
import org.mindignited.structures.internal.utils.StructuresUtil;
import org.springframework.stereotype.Component;

import com.github.slugify.Slugify;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;

@Component
public class DefaultProjectService extends AbstractCrudService<Project> implements ProjectService {

    private final StructureService structureService;
    final Slugify slg = Slugify.builder().underscoreSeparator(true).build();

    public DefaultProjectService(CrudServiceTemplate crudServiceTemplate, 
                                 ElasticsearchAsyncClient esAsyncClient,
                                 StructureService structureService) {
        super("struct_project", 
              Project.class, 
              esAsyncClient, 
              crudServiceTemplate);
        this.structureService = structureService;
    }

    @Override
    public CompletableFuture<Long> countForApplication(String applicationId) {
        return crudServiceTemplate.count(indexName, builder -> builder
        .query(q -> q
                .bool(b -> b
                        .filter(TermQuery.of(tq -> tq.field("applicationId").value(applicationId))._toQuery()
                        )
                )));
    }

    @Override
    public CompletableFuture<Project> createProjectIfNotExist(Project project) {
        Validate.notNull(project, "Project cannot be null");
        Validate.notNull(project.getName(), "Project name cannot be null");
        Validate.notNull(project.getApplicationId(), "Project applicationId cannot be null");

        if(project.getId() == null){
            String projectId = (project.getApplicationId()+"_"+slg.slugify(project.getName())).toLowerCase();
            project.setId(projectId);
        }
        // Sanity check
        StructuresUtil.validateProjectId(project.getId());

        return findById(project.getId())
                .thenCompose(existing -> {
                    if(existing != null){
                        return CompletableFuture.completedFuture(existing);
                    }else{
                        return save(project);
                    }
                });
    }

    @Override
    public CompletableFuture<Void> deleteById(String id) {
        return structureService.countForProject(id).thenAccept(count -> {
            if(count > 0){
                throw new IllegalStateException("Cannot delete project with structures in it.");
            }
        }).thenCompose(v -> super.deleteById(id));
    }


    @Override
    public CompletableFuture<Page<Project>> findAllForApplication(String applicationId, Pageable pageable) {
        return crudServiceTemplate.search(indexName, pageable, type, builder -> builder
        .query(q -> q
                .bool(b -> b.filter(TermQuery.of(tq -> tq.field("applicationId").value(applicationId))._toQuery())
                )
        ));
    }

    @Override
    public CompletableFuture<Project> save(Project project) {
        Validate.notNull(project, "Project cannot be null");
        Validate.notNull(project.getApplicationId(), "Project applicationId cannot be null");
        Validate.notNull(project.getName(), "Project name cannot be null");

        if(project.getId() == null){
            String projectId = (project.getApplicationId()+"_"+slg.slugify(project.getName())).toLowerCase();
            project.setId(projectId);
        }

        project.setUpdated(new Date());
        return super.save(project);
    }

    @Override
    public CompletableFuture<Page<Project>> search(String searchText, Pageable pageable) {
        return crudServiceTemplate.search(indexName,
                                          pageable,
                                          Project.class,
                                          builder -> builder.q(searchText));
    }

    @Override
    public CompletableFuture<Void> syncIndex() {
        return esAsyncClient.indices()
        .refresh(b -> b.index(indexName))
        .thenApply(unused -> null);
    }
}
