package org.kinotic.management.internal.api.services;

import com.github.slugify.Slugify;
import io.vertx.core.Future;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.kinotic.authz.api.model.RelationshipTuple;
import org.kinotic.authz.api.model.Resource;
import org.kinotic.authz.api.services.AuthzStoreService;
import org.kinotic.authz.api.services.RelationshipService;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.exceptions.AlreadyExistsException;
import org.kinotic.core.api.exceptions.AuthorizationException;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.domain.api.model.WatchEvent;
import org.kinotic.domain.api.model.WatchEventType;
import org.kinotic.idl.api.utils.AuthzConstants;
import org.kinotic.management.api.model.Project;
import org.kinotic.management.api.model.RepositoryHost;
import org.kinotic.management.api.model.deployment.ProjectDependencies;
import org.kinotic.management.api.model.deployment.ProjectDeployment;
import org.kinotic.management.api.model.RepositoryConnectionStatus;
import org.kinotic.management.api.repositories.ProjectDependenciesRepository;
import org.kinotic.management.api.repositories.ProjectDeploymentRepository;
import org.kinotic.management.api.repositories.ProjectRepository;
import org.kinotic.domain.api.repositories.EntityDefinitionRepository;
import org.kinotic.domain.internal.api.services.AbstractApplicationScopedService;
import org.kinotic.domain.api.utils.AuthzGraphUtil;
import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.management.api.services.EntityDefinitionService;
import org.kinotic.management.api.services.ProjectRepoProvisioner;
import org.kinotic.management.api.services.ProjectService;
import org.kinotic.management.internal.api.services.security.DefaultPermissionService;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

@Slf4j
@Component
public class DefaultProjectService extends AbstractApplicationScopedService<Project> implements ProjectService {

    final Slugify slg = Slugify.builder().build();

    private final ProjectRepository projectRepository;
    private final EntityDefinitionRepository entityDefinitionRepository;
    private final DefaultPermissionService permissions;
    private final ProjectDeploymentRepository projectDeploymentRepository;
    private final ProjectDependenciesRepository projectDependenciesRepository;
    private final ProjectRepoProvisioner repoProvisioner;
    private final RelationshipService relationships;

    public DefaultProjectService(ProjectRepository repository,
                                 EntityDefinitionRepository entityDefinitionRepository,
                                 DefaultPermissionService permissions,
                                 SecurityContext securityContext,
                                 ProjectDeploymentRepository projectDeploymentRepository,
                                 ProjectDependenciesRepository projectDependenciesRepository,
                                 ProjectRepoProvisioner repoProvisioner,
                                 RelationshipService relationships) {
        super(repository, securityContext);
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
                : permissions.holds(ProjectService.RESOURCE_TYPE, AuthzConstants.CAN_READ, id)
                             // a project the caller may not read is reached through an entity definition in it the caller reads
                             .compose(held -> held ? Future.succeededFuture(true) : visibleIds().map(ids -> ids == null || ids.contains(id)))
                             .map(visible -> visible ? project : null));
    }

    // What the caller may see, null for every project of the organization: the projects it reads, and those
    // containing an entity definition it reads, so a grant on an entity definition makes its project reachable
    private Future<Set<String>> visibleIds() {
        String organizationId = requireOrganizationId();
        return permissions.findAccessibleIds(ProjectService.RESOURCE_TYPE, AuthzConstants.CAN_READ).compose(projects -> projects == null
                ? Future.succeededFuture(null)
                : permissions.listAccessible(EntityDefinitionService.RESOURCE_TYPE, AuthzConstants.CAN_READ)
                             .compose(ids -> entityDefinitionRepository.findProjectIdsOf(ids, organizationId))
                             .compose(named -> projectRepository.findApplicationIdsById(named.keySet(), organizationId).map(stored -> {
                                 Set<String> ret = new HashSet<>(projects);
                                 // a definition names its project by an id its author chose, so the project
                                 // contains the definition only when both are in the same application
                                 stored.forEach((id, applicationId) -> {
                                     if (named.get(id).contains(applicationId)) {
                                         ret.add(id);
                                     }
                                 });
                                 return ret;
                             })));
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
                .compose(existing -> existing == null ? Future.succeededFuture(existing) : placed(project))
                .compose(placed -> {
                    Future<Project> ret;
                    if (placed != null) {
                        ret = Future.succeededFuture(placed);
                    } else {
                        // of two callers at once only the first writes the project and places it in the graph, so
                        // grants made on it since are kept
                        ret = repoProvisioner.provision(project)
                                             .compose(provisioned -> written(provisioned, super::createSync))
                                             .recover(ex -> AlreadyExistsException.isCause(ex)
                                                     ? placed(project)
                                                     : Future.failedFuture(ex));
                    }
                    return ret;
                });
    }

    // The gateway checked the caller on the application the request names, and the id is the caller's to choose,
    // so a project another application stores under that id is refused instead of returned
    private static Project requireApplicationOf(Project requested, Project stored) {
        if (stored != null && !stored.getApplicationId().equals(requested.getApplicationId())) {
            throw new IllegalArgumentException("Project for id " + stored.getId() + " already exists in another application");
        }
        return stored;
    }

    @Override
    public Future<Project> save(Project project) {
        return requireStored(project).compose(v -> super.save(project));
    }

    @Override
    public Future<Project> saveSync(Project project) {
        return requireStored(project).compose(v -> super.saveSync(project));
    }

    // A save never creates: a new project goes through create, which provisions its repository, so a member cannot
    // write a project bound to a repository of their choosing and then have that repository's credentials issued
    private Future<Void> requireStored(Project project) {
        Validate.notNull(project, "Project cannot be null");
        Validate.notNull(project.getId(), "Project id cannot be null");
        return super.findById(project.getId()).compose(stored -> stored == null
                ? Future.failedFuture(new IllegalArgumentException("Project for id " + project.getId() + " does not exist; a project is created, not saved"))
                : Future.succeededFuture());
    }

    @Override
    public Future<Void> deleteById(String id) {
        return deleted(id, super::deleteById);
    }

    @Override
    public Future<Void> deleteByIdSync(String id) {
        return deleted(id, super::deleteByIdSync);
    }

    // The project leaves the graph with every grant made on it, while no caller creates a project of its id. The
    // record is read under the lock, so a project created since the caller asked leaves the graph as well
    private Future<Void> deleted(String id, Function<String, Future<Void>> delete) {
        String object = AuthzGraphUtil.authzObject(requireOrganizationId(), new Resource(ProjectService.RESOURCE_TYPE, id));
        return relationships.exclusively(object, () -> super.findById(id)
                .compose(project -> delete.apply(id)
                                          .compose(v -> project == null
                                                  ? Future.succeededFuture()
                                                  : relationships.removeObject(AuthzStoreService.SYSTEM, object))));
    }

    // The project stored under the requested one's id, placed in the graph, or null when none is stored. A create
    // whose placement failed left the record without its place in the graph, which the record outlives since its
    // repository exists by then; ensure is idempotent, so a project placed already keeps its grants, and the engine
    // being unreachable leaves the project as it is rather than failing the caller. The record is read while no
    // other caller creates or deletes a project of the id, so the application it is placed in is the one storing it
    private Future<Project> placed(Project requested) {
        return relationships.exclusively(containment(requested).object(), () -> super.findById(requested.getId())
                .map(stored -> requireApplicationOf(requested, stored))
                .compose(stored -> stored == null
                        ? Future.succeededFuture(stored)
                        : relationships.ensure(AuthzStoreService.SYSTEM, List.of(containment(stored)))
                                       .recover(err -> {
                                           log.warn("Could not place project {} in the graph: {}", stored.getId(), err.getMessage());
                                           return Future.succeededFuture();
                                       })
                                       .map(stored)));
    }

    // Writes the project and its place in the graph while no other caller creates or deletes a project of its id,
    // which a caller chooses and the graph knows by organization, whichever application it is in. What a deleted
    // project of the id left behind, when the removal after its record's deletion failed, is removed before the
    // record is written, so the new project is never reached through a grant made on the old one, and a removal
    // that cannot be made fails the create. A placement that fails leaves a project nobody can reach rather than
    // one nobody stores, since its repository exists by then
    private Future<Project> written(Project project, Function<Project, Future<Project>> write) {
        RelationshipTuple containment = containment(project);
        return relationships.exclusively(containment.object(), () -> super.findById(project.getId())
                .compose(existing -> existing == null
                        ? relationships.removeObject(AuthzStoreService.SYSTEM, containment.object())
                        : Future.failedFuture(new AlreadyExistsException("Project for id " + project.getId() + " already exists")))
                .compose(v -> write.apply(project))
                .compose(stored -> relationships.ensure(AuthzStoreService.SYSTEM, List.of(containment)).map(stored)));
    }

    static RelationshipTuple containment(Project project) {
        return AuthzGraphUtil.containment(AuthzGraphUtil.authzApplication(project.getOrganizationId(), project.getApplicationId()),
                                          AuthzGraphUtil.authzObject(project.getOrganizationId(), new Resource(ProjectService.RESOURCE_TYPE, project.getId())));
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
        // the id names the project's checkout directory on a node, so a saved id is held to the same grammar as a created one
        DomainUtil.validateProjectId(project.getId());
        project.setUpdated(new Date());
        // The repository binding is the provisioner's: a save keeps what is stored, so a member cannot
        // point a project at another repository and have that repository's credentials issued
        return super.findById(project.getId()).map(stored -> {
            if (stored != null) {
                project.setRepoFullName(stored.getRepoFullName());
                project.setRepoId(stored.getRepoId());
                project.setRepoDefaultBranch(stored.getRepoDefaultBranch());
                project.setRepoPrivate(stored.isRepoPrivate());
                project.setRepoConnectionStatus(stored.getRepoConnectionStatus());
            }
            return null;
        });
    }

    @Override
    public Future<RepositoryHost> getRepositoryHost() {
        return Future.succeededFuture(repoProvisioner.repositoryHost());
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
    public Future<Page<WatchEvent>> findAllDeploymentHistory(List<WatchEventType> kinds, Pageable pageable) {
        Validate.notEmpty(kinds, "kinds must not be empty");
        Validate.notNull(pageable, "pageable must not be null");
        return projectDeploymentRepository.findAllHistory(requireOrganizationId(), kinds, pageable);
    }

    @Override
    public Future<ProjectDependencies> findDependencies(String projectId) {
        Validate.notBlank(projectId, "projectId must not be blank");
        String organizationId = requireOrganizationId();
        // the deployment says whether the stored tree lists the dependencies it last reported
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
            // written to the repository: the service's save keeps the stored repository binding, which the
            // initialization retry changes
            return repoProvisioner.reinitialize(project)
                                  .compose(reinitialized -> {
                                      reinitialized.setUpdated(new Date());
                                      return projectRepository.saveSync(reinitialized, reinitialized.getOrganizationId());
                                  });
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
                        // the create made again places the record before refusing the duplicate
                        return placed(project).compose(p -> Future.failedFuture(new IllegalArgumentException(
                                "Project for id " + project.getId() + " already exists")));
                    }
                    return repoProvisioner.provision(project)
                                          .compose(provisioned -> written(provisioned, write)
                                                  .recover(ex -> AlreadyExistsException.isCause(ex)
                                                          ? Future.failedFuture(new IllegalArgumentException(
                                                                  "Project for id " + project.getId() + " already exists"))
                                                          : Future.failedFuture(ex)));
                });
    }

    private void validateAndDeriveId(Project project) {
        Validate.notNull(project, "Project cannot be null");
        Validate.notNull(project.getName(), "Project name cannot be null");
        Validate.notNull(project.getApplicationId(), "Project applicationId cannot be null");
        DomainUtil.validateApplicationId(project.getApplicationId());
        // checked before the repository is provisioned under the project's organization, which the
        // write's own organization check comes too late for; a project naming no organization is the caller's
        String organizationId = requireOrganizationId();
        if (project.getOrganizationId() == null) {
            project.setOrganizationId(organizationId);
        } else if (!organizationId.equals(project.getOrganizationId())) {
            throw new AuthorizationException("Cannot create a project of organization '" + project.getOrganizationId()
                                             + "' while authenticated as organization '" + organizationId + "'");
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
