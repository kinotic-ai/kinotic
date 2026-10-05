package org.kinotic.management.internal.api.services;

import org.kinotic.authz.api.model.RelationshipTuple;
import org.kinotic.authz.api.services.AuthzStoreService;
import org.kinotic.authz.api.services.RelationshipService;
import org.kinotic.idl.api.utils.AuthzUtil;
import io.vertx.core.Future;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.exceptions.AlreadyExistsException;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.domain.api.model.ApplicationKey;
import org.kinotic.domain.api.utils.HostLabelUtil;
import org.kinotic.domain.api.model.Application;
import org.kinotic.domain.api.model.OnboardingMechanism;
import org.kinotic.domain.api.model.security.OidcConfiguration;
import org.kinotic.domain.api.repositories.ApplicationRepository;
import org.kinotic.domain.api.repositories.AuthzStoreRepository;
import org.kinotic.domain.api.repositories.EntityDefinitionRepository;
import org.kinotic.domain.internal.api.services.AbstractOrganizationScopedService;
import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.management.api.repositories.ProjectRepository;
import org.kinotic.management.api.repositories.UiDeploymentRepository;
import org.kinotic.management.api.services.ApplicationService;
import org.kinotic.management.api.services.EntityDefinitionService;
import org.kinotic.management.api.services.ProjectService;
import org.kinotic.management.api.services.security.PermissionService;
import org.kinotic.domain.api.services.security.OidcConfigurationService;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class DefaultApplicationService extends AbstractOrganizationScopedService<Application> implements ApplicationService {

    // every site label, <org>--<app>--<ui>, must leave room for at least this long a UI name
    private static final int MIN_UI_NAME_LENGTH = 1;

    private final ProjectRepository projectRepository;
    private final EntityDefinitionRepository entityDefinitionRepository;
    private final PermissionService permissions;
    private final OidcConfigurationService oidcConfigurationService;
    private final UiDeploymentRepository uiDeploymentRepository;
    private final RelationshipService relationships;
    private final ApplicationStoreProvisioner storeProvisioner;
    private final AuthzStoreService storeService;
    private final AuthzStoreRepository stores;

    public DefaultApplicationService(ApplicationRepository repository,
                                     ProjectRepository projectRepository,
                                     EntityDefinitionRepository entityDefinitionRepository,
                                     PermissionService permissions,
                                     OidcConfigurationService oidcConfigurationService,
                                     UiDeploymentRepository uiDeploymentRepository,
                                     SecurityContext securityContext,
                                     RelationshipService relationships,
                                     ApplicationStoreProvisioner storeProvisioner,
                                     AuthzStoreService storeService,
                                     AuthzStoreRepository stores) {
        super(repository, securityContext);
        this.projectRepository = projectRepository;
        this.entityDefinitionRepository = entityDefinitionRepository;
        this.permissions = permissions;
        this.oidcConfigurationService = oidcConfigurationService;
        this.uiDeploymentRepository = uiDeploymentRepository;
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
                : visibleIds().map(ids -> ids.contains(id) ? application : null));
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

    // What the caller may see: the applications it views, and those containing a project or an entity
    // definition it views, so a grant anywhere inside an application makes the application reachable
    private Future<Set<String>> visibleIds() {
        String organizationId = requireOrganizationId();
        return Future.all(permissions.listAccessible(AuthzUtil.APPLICATION_TYPE, AuthzUtil.CAN_VIEW),
                          permissions.listAccessible(ProjectService.RESOURCE_TYPE, AuthzUtil.CAN_VIEW)
                                     .compose(ids -> projectRepository.findApplicationIdsOf(ids, organizationId)),
                          permissions.listAccessible(EntityDefinitionService.RESOURCE_TYPE, AuthzUtil.CAN_VIEW)
                                     .compose(ids -> entityDefinitionRepository.findApplicationIdsOf(ids, organizationId)))
                     .map(visible -> {
                         Set<String> ret = new HashSet<>();
                         for (int i = 0; i < visible.size(); i++) {
                             ret.addAll(visible.<Collection<String>>resultAt(i));
                         }
                         return ret;
                     });
    }

    @Override
    public Future<Application> createApplicationIfNotExist(String name, String description, Set<OnboardingMechanism> onboarding) {
        String applicationId = DomainUtil.slugifyId(name);
        String organizationId = requireOrganizationId();
        return super.findById(applicationId)
                .compose(application -> {
                    Future<Application> ret;
                    if(application != null){
                        // an existing application keeps its onboarding: flipping per-user tenancy here would
                        // split its users into tenanted and untenanted halves, since only users created
                        // while it is enabled receive a tenant
                        ret = Future.succeededFuture(application);
                    }else{
                        Application newApplication = new Application(name, description);
                        newApplication.setOrganizationId(organizationId);
                        // a caller with no tenancy opinion, such as the CLI ensuring the app row exists,
                        // omits the argument and gets the shared default
                        newApplication.setOnboarding(onboarding == null ? new HashSet<>() : new HashSet<>(onboarding));
                        ret = save(newApplication).compose(this::contained);
                    }
                    return ret;
                });
    }

    @Override
    public Future<Application> create(Application entity) {
        // Force the id to derive from the name; beforeSave mints it from the slug.
        entity.setId(null);
        return failOnDuplicateName(super.create(entity), entity).compose(this::contained);
    }

    @Override
    public Future<Application> createSync(Application entity) {
        entity.setId(null);
        return failOnDuplicateName(super.createSync(entity), entity).compose(this::contained);
    }

    @Override
    public Future<Void> deleteById(String id) {
        return super.deleteById(id).compose(v -> released(id));
    }

    @Override
    public Future<Void> deleteByIdSync(String id) {
        return super.deleteByIdSync(id).compose(v -> released(id));
    }

    // The application's place in the graph, written once the record is, so a write that fails leaves an
    // application nobody can reach rather than one nobody stores; and its own store, which its users are
    // answered by
    private Future<Application> contained(Application application) {
        return relationships.ensure(AuthzStoreService.PLATFORM, List.of(containment(application.getId())))
                            .compose(v -> storeProvisioner.provision(application))
                            .map(application);
    }

    // The application leaves the graph and its store goes with everything in it, before its record does, so
    // an application created under the same id afterwards starts from an empty store rather than inheriting
    // this one's grants
    private Future<Void> released(String applicationId) {
        return relationships.remove(AuthzStoreService.PLATFORM, List.of(containment(applicationId)))
                            .compose(v -> storeService.deleteStore(applicationId))
                            .compose(v -> stores.deleteByIdSync(applicationId));
    }

    private RelationshipTuple containment(String applicationId) {
        return new RelationshipTuple(AuthzUtil.object(AuthzUtil.ORGANIZATION_TYPE, requireOrganizationId()),
                                     AuthzUtil.ORGANIZATION_TYPE,
                                     AuthzUtil.object(AuthzUtil.APPLICATION_TYPE, applicationId));
    }

    // The caller supplied a name, not the derived id an AlreadyExistsException would reference
    private static Future<Application> failOnDuplicateName(Future<Application> created,
                                                           Application entity) {
        return created.recover(ex -> AlreadyExistsException.isCause(ex)
                ? Future.failedFuture(new AlreadyExistsException(
                        "An application named '" + entity.getName() + "' already exists"))
                : Future.failedFuture(ex));
    }

    @Override
    protected Future<Void> beforeDelete(String id) {
        return projectRepository.countForApplication(id, requireOrganizationId()).compose(count -> {
            if(count > 0){
                throw new IllegalStateException("Cannot delete an application with projects in it.");
            }
            return Future.succeededFuture();
        });
    }

    @Override
    protected Future<Void> beforeSave(Application entity) {
        Validate.notNull(entity.getName(), "Application name cannot be null");
        if (entity.getOnboarding() == null) {
            entity.setOnboarding(new HashSet<>());
        }
        Validate.isTrue(!entity.getOnboarding().contains(OnboardingMechanism.TENANT_PER_USER) || entity.getOnboarding().size() == 1,
                        "An application isolating each user in a tenant of its own offers no other way into a tenant");

        if (entity.getId() == null) {
            entity.setId(DomainUtil.slugifyId(entity.getName()));
        }
        // Validate only; re-minting an update's id would silently write a new document
        DomainUtil.validateApplicationId(entity.getId());
        ApplicationKey applicationKey = new ApplicationKey(requireOrganizationId(), entity.getId());
        // neither id changes after creation, so an application too long for a site label could never publish a UI
        Validate.isTrue(HostLabelUtil.label(applicationKey).length() + HostLabelUtil.HOST_LABEL_SEPARATOR.length() + MIN_UI_NAME_LENGTH <= HostLabelUtil.MAX_HOST_LABEL_LENGTH,
                        "The application's host label '%s' leaves no room for a UI name in its sites' labels, which DNS limits"
                                + " to %d characters; shorten the application name",
                        HostLabelUtil.label(applicationKey), HostLabelUtil.MAX_HOST_LABEL_LENGTH);
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
        // a site's label names its application and UI, so a site with this label is one of this application's UIs
        return uiDeploymentRepository.findById(HostLabelUtil.siteLabel(applicationKey, uiName))
                .map(site -> {
                    Validate.isTrue(site != null, "The application '%s' has no published UI named '%s'",
                                    applicationKey.applicationId(), uiName);
                    return site.getUrl();
                });
    }

    @Override
    public Future<List<OidcConfiguration>> getOidcConfigurations(String applicationId) {
        Validate.notNull(applicationId, "applicationId cannot be null");
        return super.findById(applicationId)
                .compose(application -> {
                    Validate.notNull(application, "Application not found: %s", applicationId);
                    List<String> ids = application.getOidcConfigurationIds();
                    if (ids == null || ids.isEmpty()) {
                        return Future.succeededFuture(Collections.emptyList());
                    }
                    return oidcConfigurationService.findEnabledByIds(ids, application.getOrganizationId());
                });
    }

}
