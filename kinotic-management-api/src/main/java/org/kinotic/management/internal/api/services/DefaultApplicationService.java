package org.kinotic.management.internal.api.services;

import io.vertx.core.Future;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.kinotic.authz.api.services.AuthzStoreService;
import org.kinotic.authz.api.services.RelationshipService;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.exceptions.AlreadyExistsException;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.domain.api.model.ApplicationKey;
import org.kinotic.domain.api.utils.HostLabelUtil;
import org.kinotic.domain.api.model.Application;
import org.kinotic.domain.api.model.TenantManagement;
import org.kinotic.domain.api.model.security.PasswordConfiguration;
import org.kinotic.domain.api.model.security.ApplicationLoginProvider;
import org.kinotic.domain.api.model.security.LoginScope;
import org.kinotic.domain.api.repositories.ApplicationRepository;
import org.kinotic.domain.api.repositories.AuthzStoreRepository;
import org.kinotic.domain.api.repositories.EntityDefinitionRepository;
import org.kinotic.domain.internal.api.services.AbstractOrganizationScopedService;
import org.kinotic.domain.api.utils.AuthzGraphUtil;
import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.idl.api.utils.AuthzConstants;
import org.kinotic.management.api.repositories.ProjectRepository;
import org.kinotic.management.api.repositories.UiDeploymentRepository;
import org.kinotic.management.api.services.ApplicationService;
import org.kinotic.management.api.services.EntityDefinitionService;
import org.kinotic.management.api.services.ProjectService;
import org.kinotic.management.internal.api.services.security.DefaultPermissionService;
import org.kinotic.domain.api.services.security.ApplicationSignUpService;
import org.kinotic.domain.api.services.security.PasswordConfigurationService;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

@Slf4j
@Component
public class DefaultApplicationService extends AbstractOrganizationScopedService<Application> implements ApplicationService {

    // every site label, <app>--<ui>, must leave room for at least this long a UI name
    private static final int MIN_UI_NAME_LENGTH = 1;

    private final ProjectRepository projectRepository;
    private final EntityDefinitionRepository entityDefinitionRepository;
    private final DefaultPermissionService permissions;
    private final ApplicationSignUpService applicationSignUpService;
    private final UiDeploymentRepository uiDeploymentRepository;
    private final PasswordConfigurationService passwordConfigurationService;
    private final RelationshipService relationships;
    private final ApplicationStoreProvisioner storeProvisioner;
    private final AuthzStoreService storeService;
    private final AuthzStoreRepository stores;

    public DefaultApplicationService(ApplicationRepository repository,
                                     ProjectRepository projectRepository,
                                     EntityDefinitionRepository entityDefinitionRepository,
                                     DefaultPermissionService permissions,
                                     ApplicationSignUpService applicationSignUpService,
                                     UiDeploymentRepository uiDeploymentRepository,
                                     PasswordConfigurationService passwordConfigurationService,
                                     SecurityContext securityContext,
                                     RelationshipService relationships,
                                     ApplicationStoreProvisioner storeProvisioner,
                                     AuthzStoreService storeService,
                                     AuthzStoreRepository stores) {
        super(repository, securityContext);
        this.projectRepository = projectRepository;
        this.entityDefinitionRepository = entityDefinitionRepository;
        this.permissions = permissions;
        this.applicationSignUpService = applicationSignUpService;
        this.uiDeploymentRepository = uiDeploymentRepository;
        this.passwordConfigurationService = passwordConfigurationService;
        this.relationships = relationships;
        this.storeProvisioner = storeProvisioner;
        this.storeService = storeService;
        this.stores = stores;
    }

    // Read before the engine is asked, so a missing id never leaves a denial in the engine's caches for the
    // record's creation to outlive; the functions the gateway checked on the application read it with
    // super.findById
    @Override
    public Future<Application> findById(String id) {
        return super.findById(id).compose(application -> application == null
                ? Future.succeededFuture(null)
                : permissions.holds(AuthzConstants.APPLICATION_TYPE, AuthzConstants.CAN_READ, id)
                             // an application the caller may not read is reached through a project or an entity
                             // definition in it the caller reads
                             .compose(held -> held ? Future.succeededFuture(true) : visibleIds().map(ids -> ids == null || ids.contains(id)))
                             .map(visible -> visible ? application : null));
    }

    @Override
    public Future<Long> count() {
        return visibleIds().compose(ids -> scopedRepository.count(requireOrganizationId(), ids));
    }

    @Override
    public Future<Page<Application>> findAll(Pageable pageable) {
        return visibleIds().compose(ids -> scopedRepository.findAll(requireOrganizationId(), ids, pageable));
    }

    @Override
    public Future<Page<Application>> search(String searchText, Pageable pageable) {
        return visibleIds().compose(ids -> scopedRepository.search(searchText, requireOrganizationId(), ids, pageable));
    }

    // What the caller may see, null for every application of the organization: the applications it reads, and
    // those containing a project or an entity definition it reads, so a grant anywhere inside an application makes
    // the application reachable
    private Future<Set<String>> visibleIds() {
        String organizationId = requireOrganizationId();
        return permissions.findAccessibleIds(AuthzConstants.APPLICATION_TYPE, AuthzConstants.CAN_READ).compose(applications -> applications == null
                ? Future.succeededFuture(null)
                : Future.all(permissions.listAccessible(ProjectService.RESOURCE_TYPE, AuthzConstants.CAN_READ)
                                        .compose(ids -> projectRepository.findApplicationIdsOf(ids, organizationId)),
                             permissions.listAccessible(EntityDefinitionService.RESOURCE_TYPE, AuthzConstants.CAN_READ)
                                        .compose(ids -> entityDefinitionRepository.findApplicationIdsOf(ids, organizationId)))
                        .map(containing -> {
                            Set<String> ret = new HashSet<>(applications);
                            for (int i = 0; i < containing.size(); i++) {
                                ret.addAll(containing.<Collection<String>>resultAt(i));
                            }
                            return ret;
                        }));
    }

    @Override
    public Future<Application> createApplicationIfNotExist(String name, String description, TenantManagement tenantManagement) {
        String applicationId = DomainUtil.slugifyId(name);
        String organizationId = requireOrganizationId();
        return exclusively(applicationId, () -> super.findById(applicationId)
                .compose(application -> {
                    Future<Application> ret;
                    if(application != null){
                        // an existing application keeps its tenant policy: flipping it here would
                        // split its users into tenanted and untenanted halves, since only users
                        // created while it is enabled receive a tenant. Provisioning writes the store's record
                        // last, so an application with no record was left by a server that stopped while
                        // provisioning it; it is provisioned here, which is idempotent, so it is not returned
                        // before its store exists
                        ret = stores.findById(AuthzGraphUtil.authzApplicationId(organizationId, applicationId))
                                    .compose(record -> record != null
                                            ? Future.succeededFuture(application)
                                            : storeProvisioner.provision(application));
                    }else{
                        Application newApplication = new Application(name, description);
                        newApplication.setOrganizationId(organizationId);
                        // a caller with no tenancy opinion, such as the CLI ensuring the app row exists,
                        // omits the argument and leaves tenants to their administrators
                        newApplication.setTenantManagement(tenantManagement != null ? tenantManagement : TenantManagement.TENANT_ADMINISTRATORS);
                        ret = super.create(newApplication).compose(this::provisioned);
                    }
                    return ret;
                }));
    }

    @Override
    public Future<Application> create(Application entity) {
        return created(entity, super::create);
    }

    @Override
    public Future<Application> createSync(Application entity) {
        return created(entity, super::createSync);
    }

    private Future<Application> created(Application entity, Function<Application, Future<Application>> create) {
        // the id always derives from the name, whatever id the caller sent
        entity.setId(DomainUtil.slugifyId(entity.getName()));
        return exclusively(entity.getId(), () -> failOnDuplicateName(create.apply(entity), entity).compose(this::provisioned));
    }

    @Override
    public Future<Void> deleteById(String id) {
        return exclusively(id, () -> super.deleteById(id));
    }

    @Override
    public Future<Void> deleteByIdSync(String id) {
        return exclusively(id, () -> super.deleteByIdSync(id));
    }

    // One caller at a time creates, provisions or deletes the application of an id. A create whose store fails to
    // provision deletes the application again, so a caller finding the record meanwhile would be answered with an
    // application about to be deleted, and a delete would leave the store a create is still writing
    private <T> Future<T> exclusively(String applicationId, Supplier<Future<T>> changes) {
        return relationships.exclusively(AuthzGraphUtil.authzApplication(requireOrganizationId(), applicationId), changes);
    }

    // An application whose store could not be provisioned is deleted again with whatever provisioning wrote, so the
    // create can be made again
    private Future<Application> provisioned(Application application) {
        return storeProvisioner.provision(application)
                               .recover(e -> scopedRepository.deleteByIdSync(application.getId(), application.getOrganizationId())
                                                             .compose(v -> released(application.getId()))
                                                             // the caller sees the provisioning failure, so a record or store
                                                             // the rollback left behind is reported here
                                                             .onFailure(rollback -> log.warn("Could not roll back application {} after its store failed to provision",
                                                                                             application.getId(), rollback))
                                                             .transform(deleted -> Future.<Application>failedFuture(e)));
    }

    // The application leaves the graph with every grant made on it, and its store goes with everything in it, so an
    // application created under the same id afterwards starts with no grant and an empty store
    private Future<Void> released(String applicationId) {
        String organizationId = requireOrganizationId();
        String store = AuthzGraphUtil.authzApplicationId(organizationId, applicationId);
        return relationships.removeObject(AuthzStoreService.SYSTEM, AuthzGraphUtil.authzApplication(organizationId, applicationId))
                            .compose(v -> storeService.deleteStore(store))
                            .compose(v -> stores.deleteByIdSync(store));
    }

    // The caller supplied a name, not the derived id an AlreadyExistsException would reference
    private static Future<Application> failOnDuplicateName(Future<Application> created,
                                                           Application entity) {
        return created.recover(ex -> AlreadyExistsException.isCause(ex)
                ? Future.failedFuture(new AlreadyExistsException(
                        "An application named '" + entity.getName() + "' already exists"))
                : Future.failedFuture(ex));
    }

    // the graph before the record: a record that outlives its graph is deleted again, while a store that outlived
    // its record would be adopted by an application created under the same id
    @Override
    protected Future<Void> beforeDelete(String id) {
        return projectRepository.countForApplication(id, requireOrganizationId()).compose(count -> {
            if(count > 0){
                throw new IllegalStateException("Cannot delete an application with projects in it.");
            }
            // an application created again under the same id starts with no password sign-in
            String organizationId = requireOrganizationId();
            return passwordConfigurationService.deleteById(PasswordConfiguration.idOf(organizationId, id, null), organizationId)
                                               .compose(v -> released(id));
        });
    }

    @Override
    protected Future<Void> beforeSave(Application entity) {
        Validate.notNull(entity.getName(), "Application name cannot be null");

        if (entity.getId() == null) {
            entity.setId(DomainUtil.slugifyId(entity.getName()));
        }
        // Validate only; re-minting an update's id would silently write a new document
        DomainUtil.validateApplicationId(entity.getId());
        ApplicationKey applicationKey = new ApplicationKey(requireOrganizationId(), entity.getId());
        // the id never changes after creation, so an application whose API host label exceeds DNS's limit could
        // never be reached, and one too long for a site label could never publish a UI
        Validate.isTrue(HostLabelUtil.label(applicationKey).length() <= HostLabelUtil.MAX_HOST_LABEL_LENGTH,
                        "The application's API host label '%s' exceeds %d characters; shorten the application name",
                        HostLabelUtil.label(applicationKey), HostLabelUtil.MAX_HOST_LABEL_LENGTH);
        Validate.isTrue(entity.getId().length() + HostLabelUtil.HOST_LABEL_SEPARATOR.length() + MIN_UI_NAME_LENGTH <= HostLabelUtil.MAX_SITE_LABEL_LENGTH,
                        "The application id '%s' leaves no room for a UI name in its sites' labels, which are held to"
                                + " %d characters so a tenant's name fits in front; shorten the application name",
                        entity.getId(), HostLabelUtil.MAX_SITE_LABEL_LENGTH);
        entity.setUpdated(new Date());
        Future<String> primaryUiUrl;
        if (entity.getPrimaryUiId() == null) {
            primaryUiUrl = Future.succeededFuture();
        } else {
            // resolved only when it changes, so removing the primary UI's deployment never fails the application's other edits
            primaryUiUrl = super.findById(entity.getId())
                    .compose(stored -> stored != null && entity.getPrimaryUiId().equals(stored.getPrimaryUiId())
                            ? Future.succeededFuture(stored.getPrimaryUiUrl())
                            : publishedUiUrl(applicationKey, entity.getPrimaryUiId()));
        }
        return primaryUiUrl.compose(url -> {
            entity.setPrimaryUiUrl(url);
            return Future.succeededFuture();
        });
    }

    private Future<String> publishedUiUrl(ApplicationKey applicationKey, String uiName) {
        // a site's id names its organization, application and UI, so a site with this id is one of this application's UIs
        return uiDeploymentRepository.findById(HostLabelUtil.siteId(applicationKey, uiName))
                .map(site -> {
                    Validate.isTrue(site != null, "The application '%s' has no published UI named '%s'",
                                    applicationKey.applicationId(), uiName);
                    return site.getUrl();
                });
    }

    @Override
    public Future<List<ApplicationLoginProvider>> getLoginMethods(String applicationId) {
        Validate.notNull(applicationId, "applicationId cannot be null");
        return super.findById(applicationId).compose(application -> {
            Validate.isTrue(application != null, "The organization has no application %s", applicationId);
            return applicationSignUpService.findLoginProviders(new LoginScope(application, null));
        });
    }

}
