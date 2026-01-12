package org.mindignited.structures.internal.api.services.impl;

import java.util.Date;
import java.util.concurrent.CompletableFuture;

import org.mindignited.continuum.core.api.crud.Page;
import org.mindignited.continuum.core.api.crud.Pageable;
import org.mindignited.structures.api.domain.Application;
import org.mindignited.structures.api.services.ApplicationService;
import org.mindignited.structures.api.services.ProjectService;
import org.mindignited.structures.internal.utils.StructuresUtil;
import org.springframework.stereotype.Component;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;

@Component
public class DefaultApplicationService extends AbstractCrudService<Application> implements ApplicationService {

    private final ProjectService projectService;

    public DefaultApplicationService(ElasticsearchAsyncClient esAsyncClient,
                                     ProjectService projectService,
                                     CrudServiceTemplate crudServiceTemplate) {
        super("struct_application",
              Application.class,
              esAsyncClient,
              crudServiceTemplate);
        this.projectService = projectService;
    }

    @Override
    public CompletableFuture<Application> createApplicationIfNotExist(String id, String description) {
        StructuresUtil.validateApplicationId(id);
        return findById(id)
                .thenCompose(application -> {
                    if(application != null){
                        return CompletableFuture.completedFuture(application);
                    }else{
                        return save(new Application(id, description));
                    }
                });
    }

    @Override
    public CompletableFuture<Void> deleteById(String id) {
        return projectService.countForApplication(id).thenAccept(count -> {
            if(count > 0){
                throw new IllegalStateException("Cannot delete an application with projects in it.");
            }
        }).thenCompose(v -> super.deleteById(id));
    }

    @Override
    public CompletableFuture<Application> save(Application entity) {
        StructuresUtil.validateApplicationId(entity.getId());
        entity.setUpdated(new Date());
        return super.save(entity);
    }

    @Override
    public CompletableFuture<Page<Application>> search(String searchText, Pageable pageable) {
        return crudServiceTemplate.search(indexName,
                                          pageable,
                                          Application.class,
                                          builder -> builder.q(searchText));
    }

    @Override
    public CompletableFuture<Void> syncIndex() {
        return esAsyncClient.indices()
                            .refresh(b -> b.index(indexName))
                            .thenApply(unused -> null);
    }
}
