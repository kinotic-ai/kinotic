package org.kinotic.management.internal.api.services;

import org.kinotic.authz.api.model.RelationshipTuple;
import org.kinotic.authz.api.services.AuthzStoreService;
import org.kinotic.authz.api.services.RelationshipService;
import org.kinotic.idl.api.utils.AuthzUtil;
import com.github.slugify.Slugify;
import io.vertx.core.Future;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.exceptions.AlreadyExistsException;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.core.api.security.SecurityExceptionFactory;
import org.kinotic.domain.api.model.WatchEvent;
import org.kinotic.domain.api.model.WatchEventKind;
import org.kinotic.management.api.model.Project;
import org.kinotic.management.api.model.deployment.ProjectDependencies;
import org.kinotic.management.api.model.deployment.ProjectDeployment;
import org.kinotic.management.api.model.RepositoryConnectionStatus;
import org.kinotic.management.api.repositories.ProjectDependenciesRepository;
import org.kinotic.management.api.repositories.ProjectDeploymentRepository;
import org.kinotic.management.api.repositories.ProjectRepository;
import org.kinotic.domain.api.repositories.EntityDefinitionRepository;
import org.kinotic.domain.internal.api.services.AbstractApplicationScopedService;
import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.management.api.services.EntityDefinitionService;
import org.kinotic.management.api.services.ProjectRepoProvisioner;
import org.kinotic.management.api.services.ProjectService;
import org.kinotic.management.api.services.security.PermissionService;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

@Component
public class DefaultProjectService extends AbstractApplicationScopedService<Project> implements ProjectService {

    final Slugify slg = Slugify.builder().build();

    private final ProjectRepository projectRepository;
    private final EntityDefinitionRepository entityDefinitionRepository;
    private final PermissionService permissions;
    private final ProjectDeploymentRepository projectDeploymentRepository;
    private final ProjectDependenciesRepository projectDependenciesRepository;
    private final ProjectRepoProvisioner repoProvisioner;
    private final RelationshipService relationships;

    public DefaultProjectService(ProjectRepository repository,
                                 EntityDefinitionRepository entityDefinitionRepository,
                                 PermissionService permissions,
                                 SecurityContext securityContext,
                                 SecurityExceptionFactory securityExceptions,
                                 ProjectDeploymentRepository projectDeploymentRepository,
                                 ProjectDependenciesRepository projectDependenciesRepository,
                                 ProjectRepoProvisioner repoProvisioner,
                                 RelationshipService relationships) {
        super(repository, securityContext, securityExceptions);
        this.projectRepository = repository;
        this.entityDefinitionRepository = entityDefinitionRepository;
        this.permissions = permissions;
        this.projectDeploymentRepository = projectDeploymentRepository;
        this.projectDependenciesRepository = projectDependenciesRepository;
        this.repoProvisioner = repoProvisioner;
        this.relationships = relationships;
    }

    @Override
    public Future<Long> count() {
        return visibleIds().compose(ids -> projectRepository.count(requireOrganizationId(), ids));
    }

    @Override
    public Future<Page<Project>> findAll(Pageable pageable) {
        return visibleIds().compose(ids -> projectRepository.findAll(requireOrganizationId(), ids, pageable));
    }

    @Override
    public Future<Page<Project>> search(String searchText, Pageable pageable) {
        return visibleIds().compose(ids -> projectRepository.search(searchText, requireOrganizationId(), ids, pageable));
    }

    @Override
    public Future<Long> countForApplication(String applicationId) {
        return visibleIds().compose(ids -> projectRepository.countForApplication(applicationId, requireOrganizationId(), ids));
    }

    @Override
    public Future<Page<Project>> findAllForApplication(String applicationId, Pageable pageable) {
        return visibleIds().compose(ids -> projectRepository.findAllForApplication(applicationId, requireOrganizationId(), ids, pageable));
    }

    // Read before the engine is asked, so a missing id never leaves a denial in the engine's caches for the
    // record's creation to outlive; the functions the gateway checked on the project read it with super.findById
    @Override
    public Future<Project> findById(String id) {
        return super.findById(id).compose(project -> project == null
                ? Future.succeededFuture(null)
                : visibleIds().map(ids -> ids.contains(id) ? project : null));
    }

    // What the caller may see: the projects it views, and those containing an entity definition it views, so a
    // grant on an entity definition makes its project reachable
    private Future<Set<String>> visibleIds() {
        String organizationId = requireOrganizationId();
        return Future.all(permissions.listAccessible(ProjectService.RESOURCE_TYPE, AuthzUtil.CAN_VIEW),
                          permissions.listAccessible(EntityDefinitionService.RESOURCE_TYPE, AuthzUtil.CAN_VIEW)
                                     .compose(ids -> entityDefinitionRepository.findProjectIdsOf(ids, organizationId)))
                     .map(visible -> {
                         Set<String> ret = new HashSet<>(visible.<Collection<String>>resultAt(0));
                         ret.addAll(visible.<Collection<String>>resultAt(1));
                         return ret;
                     });
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
        return super.findById(project.getId())
                .compose(existing -> {
                    if (existing != null) {
                        return Future.succeededFuture(existing);
                    }
                    return repoProvisioner.provision(project).compose(this::save).compose(this::contained);
                });
    }

    @Override
    public Future<Void> deleteById(String id) {
        return super.findById(id).compose(project -> super.deleteById(id).compose(v -> uncontained(project)));
    }

    @Override
    public Future<Void> deleteByIdSync(String id) {
        return super.findById(id).compose(project -> super.deleteByIdSync(id).compose(v -> uncontained(project)));
    }

    // The project's place in the graph, written once the record is, so a write that fails leaves a project
    // nobody can reach rather than one nobody stores
    private Future<Project> contained(Project project) {
        return relationships.ensure(AuthzStoreService.PLATFORM, List.of(containment(project))).map(project);
    }

    private Future<Void> uncontained(Project project) {
        return project == null ? Future.succeededFuture() : relationships.remove(AuthzStoreService.PLATFORM, List.of(containment(project)));
    }

    private static RelationshipTuple containment(Project project) {
        return new RelationshipTuple(AuthzUtil.object(AuthzUtil.APPLICATION_TYPE,
                                                      DomainUtil.authzApplicationId(project.getOrganizationId(), project.getApplicationId())),
                                     AuthzUtil.APPLICATION_TYPE,
                                     AuthzUtil.object(ProjectService.RESOURCE_TYPE,
                                                      DomainUtil.authzId(ProjectService.RESOURCE_TYPE, project.getOrganizationId(), project.getId())));
    }

    @Override
    protected Future<Void> beforeSave(Project project) {
        Validate.notNull(project, "Project cannot be null");
        Validate.notNull(project.getApplicationId(), "Project applicationId cannot be null");
        Validate.notNull(project.getName(), "Project name cannot be null");
        DomainUtil.validateApplicationId(project.getApplicationId());

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

    // The deployment's removal is intent: its worker, in the system plane, asks for the removal of
    // the microservice and UI deployments, stops the workloads, removes the machines and deletes
    // the records, which this management-plane delete cannot reach itself
    @Override
    protected Future<Void> beforeDelete(String projectId) {
        String organizationId = requireOrganizationId();
        return projectDeploymentRepository.findById(projectId, organizationId)
                .compose(deployment -> deployment == null
                        ? Future.succeededFuture()
                        : projectDeploymentRepository.requestDeletion(projectId, organizationId, "deletion of project " + projectId));
    }

    @Override
    public Future<ProjectDeployment> findDeployment(String projectId) {
        Validate.notBlank(projectId, "projectId must not be blank");
        return projectDeploymentRepository.findById(projectId, requireOrganizationId());
    }

    @Override
    public Future<Page<WatchEvent>> findDeploymentHistory(String projectId, Pageable pageable) {
        Validate.notBlank(projectId, "projectId must not be blank");
        Validate.notNull(pageable, "pageable must not be null");
        return projectDeploymentRepository.findHistory(projectId, requireOrganizationId(), pageable);
    }

    @Override
    public Future<Page<WatchEvent>> findAllDeploymentHistory(List<WatchEventKind> kinds, Pageable pageable) {
        Validate.notEmpty(kinds, "kinds must not be empty");
        Validate.notNull(pageable, "pageable must not be null");
        return projectDeploymentRepository.findAllHistory(requireOrganizationId(), kinds, pageable);
    }

    @Override
    public Future<ProjectDependencies> findDependencies(String projectId) {
        Validate.notBlank(projectId, "projectId must not be blank");
        String organizationId = requireOrganizationId();
        // the tree of earlier dependencies stays stored after a sync reports new ones, until the
        // deployment's next SBOM replaces it, so the deployment says whether it is current
        return projectDeploymentRepository.findById(projectId, organizationId)
                .compose(deployment -> deployment != null && deployment.isSbomGenerated()
                        ? projectDependenciesRepository.findById(projectId, organizationId)
                        : Future.succeededFuture());
    }

    @Override
    public Future<Project> retryRepoInitialization(String projectId) {
        Validate.notBlank(projectId, "projectId must not be blank");
        return super.findById(projectId).compose(project -> {
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
        return super.findById(project.getId())
                .compose(existing -> {
                    if (existing != null) {
                        return Future.failedFuture(new IllegalArgumentException(
                                "Project for id " + project.getId() + " already exists"));
                    }
                    return repoProvisioner.provision(project)
                                          .compose(write)
                                          .compose(this::contained);
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
        DomainUtil.validateApplicationId(project.getApplicationId());
        // the caller's organization is the only one it may create in
        if (project.getOrganizationId() == null) {
            project.setOrganizationId(requireOrganizationId());
        }
        if (project.getId() == null) {
            project.setId(deriveId(project));
        }
        DomainUtil.validateProjectId(project.getId());
    }

    private String deriveId(Project project) {
        return (project.getApplicationId() + "-" + slg.slugify(project.getName())).toLowerCase();
    }

}
