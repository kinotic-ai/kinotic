package org.kinotic.os.internal.api.services;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import com.github.slugify.Slugify;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.os.api.model.Project;
import org.kinotic.os.api.services.ProjectService;
import org.kinotic.os.api.utils.DomainUtil;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.concurrent.CompletableFuture;

@Component
public class DefaultProjectService extends AbstractCrudService<Project> implements ProjectService {

    final Slugify slg = Slugify.builder().underscoreSeparator(true).build();

    public DefaultProjectService(CrudServiceTemplate crudServiceTemplate,
                                 ElasticsearchAsyncClient esAsyncClient,
                                 SecurityContext participantContext) {
        super("kinotic_project",
              Project.class,
              esAsyncClient,
              crudServiceTemplate,
              participantContext);
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
        DomainUtil.validateProjectId(project.getId());

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
        // FIXME figure out how to check if project can be deleted
//        return structureService.countForProject(id).thenAccept(count -> {
//            if(count > 0){
//                throw new IllegalStateException("Cannot delete project with structures in it.");
//            }
//        }).thenCompose(v -> super.deleteById(id));
        return super.deleteById(id);
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

}
