package org.kinotic.os.internal.api.services;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import com.github.slugify.Slugify;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.os.api.model.Project;
import org.kinotic.os.api.services.ProjectRepoProvisioner;
import org.kinotic.os.api.services.ProjectService;
import org.kinotic.os.api.utils.DomainUtil;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Component
public class DefaultProjectService extends AbstractApplicationCrudService<Project> implements ProjectService {

    private static final String INDEX = "kinotic_project";

    final Slugify slg = Slugify.builder().underscoreSeparator(true).build();

    private final ProjectRepoProvisioner repoProvisioner;

    public DefaultProjectService(CrudServiceTemplate crudServiceTemplate,
                                 ElasticsearchAsyncClient esAsyncClient,
                                 SecurityContext securityContext,
                                 ProjectRepoProvisioner repoProvisioner) {
        super(INDEX,
              Project.class,
              esAsyncClient,
              crudServiceTemplate,
              securityContext);
        this.repoProvisioner = repoProvisioner;
    }

    @Override
    public CompletableFuture<Project> create(Project project) {
        validateAndDeriveId(project);
        return findById(project.getId())
                .thenCompose(existing -> {
                    if (existing != null) {
                        return CompletableFuture.failedFuture(new IllegalArgumentException(
                                "Project for id " + project.getId() + " already exists"));
                    }
                    return provisionAndSave(project);
                });
    }

    @Override
    public CompletableFuture<Project> createProjectIfNotExist(Project project) {
        validateAndDeriveId(project);
        return findById(project.getId())
                .thenCompose(existing -> {
                    if (existing != null) {
                        return CompletableFuture.completedFuture(existing);
                    }
                    return provisionAndSave(project);
                });
    }

    @Override
    public CompletableFuture<Void> deleteById(String id) {
        return super.deleteById(id);
    }

    @Override
    public CompletableFuture<Project> save(Project project) {
        Validate.notNull(project, "Project cannot be null");
        Validate.notNull(project.getApplicationId(), "Project applicationId cannot be null");
        Validate.notNull(project.getName(), "Project name cannot be null");

        if(project.getId() == null){
            project.setId(deriveId(project));
        }
        project.setUpdated(new Date());
        return super.save(project);
    }

    @Override
    public CompletableFuture<List<Project>> findByRepoFullName(String repoFullName) {
        Validate.notBlank(repoFullName, "repoFullName must not be blank");
        String orgId = getOrganizationIdIfEnforced();
        Query q = Query.of(qb -> qb.bool(b -> {
            b.filter(TermQuery.of(t -> t.field("repoFullName").value(repoFullName))._toQuery());
            if (orgId != null) {
                b.filter(TermQuery.of(t -> t.field("organizationId").value(orgId))._toQuery());
            }
            return b;
        }));
        return crudServiceTemplate.search(INDEX, Pageable.ofSize(50), Project.class, b -> {
                                              if (orgId != null) b.routing(orgId);
                                              b.query(q);
                                          })
                                  .thenApply(page -> page.getContent());
    }

    private CompletableFuture<Project> provisionAndSave(Project project) {
        return repoProvisioner.provision(project).thenCompose(this::save);
    }

    private void validateAndDeriveId(Project project) {
        Validate.notNull(project, "Project cannot be null");
        Validate.notNull(project.getName(), "Project name cannot be null");
        Validate.notNull(project.getApplicationId(), "Project applicationId cannot be null");
        if (project.getId() == null) {
            project.setId(deriveId(project));
        }
        DomainUtil.validateProjectId(project.getId());
    }

    private String deriveId(Project project) {
        return (project.getApplicationId() + "_" + slg.slugify(project.getName())).toLowerCase();
    }

}
