package org.kinotic.management.internal.api.services;

import com.github.slugify.Slugify;
import io.vertx.core.Future;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.exceptions.AlreadyExistsException;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.management.api.model.MicroserviceDeployment;
import org.kinotic.management.api.model.Project;
import org.kinotic.management.api.model.ProjectDeployment;
import org.kinotic.management.api.model.RepositoryConnectionStatus;
import org.kinotic.management.api.model.UiDeployment;
import org.kinotic.management.api.repositories.MicroserviceDeploymentRepository;
import org.kinotic.management.api.repositories.ProjectDeploymentRepository;
import org.kinotic.management.api.repositories.ProjectRepository;
import org.kinotic.management.api.repositories.UiDeploymentRepository;
import org.kinotic.domain.internal.api.services.AbstractApplicationScopedService;
import org.kinotic.domain.api.services.security.ParticipantIdentityService;
import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.management.api.services.ProjectRepoProvisioner;
import org.kinotic.management.api.services.ProjectService;
import org.kinotic.management.api.services.UiDeploymentService;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;

@Component
public class DefaultProjectService extends AbstractApplicationScopedService<Project> implements ProjectService {

    final Slugify slg = Slugify.builder().build();

    private final ProjectRepository projectRepository;
    private final ProjectDeploymentRepository projectDeploymentRepository;
    private final MicroserviceDeploymentRepository microserviceDeploymentRepository;
    private final UiDeploymentRepository uiDeploymentRepository;
    private final UiDeploymentService uiDeploymentService;
    private final ProjectRepoProvisioner repoProvisioner;
    private final ParticipantIdentityService participantIdentityService;

    public DefaultProjectService(ProjectRepository repository,
                                 SecurityContext securityContext,
                                 ProjectDeploymentRepository projectDeploymentRepository,
                                 MicroserviceDeploymentRepository microserviceDeploymentRepository,
                                 UiDeploymentRepository uiDeploymentRepository,
                                 UiDeploymentService uiDeploymentService,
                                 ProjectRepoProvisioner repoProvisioner,
                                 ParticipantIdentityService participantIdentityService) {
        super(repository, securityContext);
        this.projectRepository = repository;
        this.projectDeploymentRepository = projectDeploymentRepository;
        this.microserviceDeploymentRepository = microserviceDeploymentRepository;
        this.uiDeploymentRepository = uiDeploymentRepository;
        this.uiDeploymentService = uiDeploymentService;
        this.repoProvisioner = repoProvisioner;
        this.participantIdentityService = participantIdentityService;
    }

    @Override
    public Future<Project> create(Project project) {
        return provisionAndWrite(project, super::create);
    }

    @Override
    public Future<Project> createSync(Project project) {
        return provisionAndWrite(project, super::createSync);
    }

    @Override
    public Future<Project> createProjectIfNotExist(Project project) {
        validateAndDeriveId(project);
        return findById(project.getId())
                .compose(existing -> {
                    if (existing != null) {
                        return Future.succeededFuture(existing);
                    }
                    return repoProvisioner.provision(project).compose(this::save);
                });
    }

    @Override
    protected Future<Void> beforeSave(Project project) {
        Validate.notNull(project, "Project cannot be null");
        Validate.notNull(project.getApplicationId(), "Project applicationId cannot be null");
        Validate.notNull(project.getName(), "Project name cannot be null");

        if(project.getId() == null){
            project.setId(deriveId(project));
        }
        project.setUpdated(new Date());
        return Future.succeededFuture();
    }

    @Override
    public Future<List<Project>> findByRepoFullName(String repoFullName) {
        Validate.notBlank(repoFullName, "repoFullName must not be blank");
        return projectRepository.findByRepoFullName(repoFullName, requireOrganizationId());
    }

    @Override
    protected Future<Void> beforeDelete(String projectId) {
        String organizationId = requireOrganizationId();
        return projectDeploymentRepository.findById(projectId, organizationId)
                .compose(deployment -> {
                    Future<Void> ret;
                    if (deployment == null) {
                        ret = Future.succeededFuture();
                    } else {
                        ret = removeMicroserviceDeployments(projectId, deployment.getSyncMachineIdentityId())
                                .compose(v -> removeUiDeployments(projectId))
                                .compose(v -> projectDeploymentRepository.deleteById(projectId, organizationId));
                    }
                    return ret;
                });
    }

    // The machines outlive the project unless they go with it, and they hold the
    // organization's authority; ParticipantIdentityService's own beforeDelete cascades each
    // one's stored credential. The checkout and the runtime workloads on the node are
    // system-side resources this management-plane delete cannot reach — deleting a
    // microservice's machine is what cuts an orphaned guest off, on its next reconnect
    private Future<Void> removeMicroserviceDeployments(String projectId, String syncMachineIdentityId) {
        return microserviceDeploymentRepository.findAllForProject(projectId)
                .compose(microservices -> deleteMachines(Stream.concat(
                                Stream.of(syncMachineIdentityId),
                                microservices.stream().map(MicroserviceDeployment::getMachineIdentityId)).toList())
                        .compose(v -> Future.all(microservices.stream()
                                                              .map(microservice -> microserviceDeploymentRepository.deleteById(microservice.getId()))
                                                              .toList()))
                        .mapEmpty());
    }

    // The sites and files of the project's UIs are management-side, so they go with it.
    // Sequential: each removal is a series of writes on the one Front Door profile
    private Future<Void> removeUiDeployments(String projectId) {
        return uiDeploymentRepository.findAllForProject(projectId)
                .compose(uis -> {
                    Future<Void> removed = Future.succeededFuture();
                    for (UiDeployment ui : uis) {
                        removed = removed.compose(v -> uiDeploymentService.remove(ui.getId()));
                    }
                    return removed;
                });
    }

    private Future<Void> deleteMachines(List<String> machineIdentityIds) {
        List<Future<Void>> deletes = machineIdentityIds.stream()
                                           .filter(Objects::nonNull)
                                           .map(participantIdentityService::deleteById)
                                           .toList();
        return Future.all(deletes).mapEmpty();
    }

    @Override
    public Future<ProjectDeployment> findDeployment(String projectId) {
        Validate.notBlank(projectId, "projectId must not be blank");
        return projectDeploymentRepository.findById(projectId, requireOrganizationId());
    }

    @Override
    public Future<Project> retryRepoInitialization(String projectId) {
        Validate.notBlank(projectId, "projectId must not be blank");
        return findById(projectId).compose(project -> {
            if (project == null) {
                return Future.failedFuture(new IllegalArgumentException(
                        "Project for id " + projectId + " does not exist"));
            }
            if (project.getRepoConnectionStatus() != RepositoryConnectionStatus.INITIALIZATION_FAILED) {
                return Future.failedFuture(new IllegalStateException(
                        "Project " + projectId + " is not awaiting initialization retry (status "
                        + project.getRepoConnectionStatus() + ")"));
            }
            return repoProvisioner.reinitialize(project).compose(this::saveSync);
        });
    }

    private Future<Project> provisionAndWrite(Project project,
                                              Function<Project, Future<Project>> write) {
        validateAndDeriveId(project);
        // Fail fast on a known duplicate before provisioning a repo; the atomic write
        // catches the race where another create lands between this check and it.
        return findById(project.getId())
                .compose(existing -> {
                    if (existing != null) {
                        return Future.failedFuture(new IllegalArgumentException(
                                "Project for id " + project.getId() + " already exists"));
                    }
                    return repoProvisioner.provision(project)
                                          .compose(write);
                })
                .recover(ex -> AlreadyExistsException.isCause(ex)
                        ? Future.failedFuture(new IllegalArgumentException(
                                "Project for id " + project.getId() + " already exists"))
                        : Future.failedFuture(ex));
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
        return (project.getApplicationId() + "-" + slg.slugify(project.getName())).toLowerCase();
    }

}
