package org.kinotic.os.internal.api.services;

import com.github.slugify.Slugify;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.domain.api.model.Project;
import org.kinotic.domain.api.model.RepositoryConnectionStatus;
import org.kinotic.domain.internal.api.repositories.ProjectRepository;
import org.kinotic.domain.internal.api.services.AbstractApplicationScopedService;
import org.kinotic.domain.internal.utils.DomainUtil;
import org.kinotic.domain.api.services.ProjectRepoProvisioner;
import org.kinotic.os.api.services.ProjectService;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Component
public class DefaultProjectService extends AbstractApplicationScopedService<Project> implements ProjectService {

    final Slugify slg = Slugify.builder().underscoreSeparator(true).build();

    private final ProjectRepository projectRepository;
    private final ProjectRepoProvisioner repoProvisioner;

    public DefaultProjectService(ProjectRepository repository,
                                 SecurityContext securityContext,
                                 ProjectRepoProvisioner repoProvisioner) {
        super(repository, securityContext);
        this.projectRepository = repository;
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
        return projectRepository.findByRepoFullName(repoFullName, requireOrganizationId());
    }

    @Override
    public CompletableFuture<Project> retryRepoInitialization(String projectId) {
        Validate.notBlank(projectId, "projectId must not be blank");
        return findById(projectId).thenCompose(project -> {
            if (project == null) {
                return CompletableFuture.failedFuture(new IllegalArgumentException(
                        "Project for id " + projectId + " does not exist"));
            }
            if (project.getRepositoryConnectionStatus() != RepositoryConnectionStatus.INITIALIZATION_FAILED) {
                return CompletableFuture.failedFuture(new IllegalStateException(
                        "Project " + projectId + " is not awaiting initialization retry (status "
                        + project.getRepositoryConnectionStatus() + ")"));
            }
            return repoProvisioner.reinitialize(project).thenCompose(this::save);
        });
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
