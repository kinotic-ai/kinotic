package org.kinotic.management.internal.api.services;

import io.vertx.core.Future;
import org.apache.commons.lang3.Validate;
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
import org.kinotic.domain.internal.api.services.AbstractOrganizationScopedService;
import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.management.api.repositories.UiDeploymentRepository;
import org.kinotic.management.api.services.ApplicationService;
import org.kinotic.management.api.services.ProjectService;
import org.kinotic.domain.api.services.security.ApplicationSignUpService;
import org.kinotic.domain.api.services.security.PasswordConfigurationService;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

@Component
public class DefaultApplicationService extends AbstractOrganizationScopedService<Application> implements ApplicationService {

    // every site label, <app>--<ui>, must leave room for at least this long a UI name
    private static final int MIN_UI_NAME_LENGTH = 1;

    private final ProjectService projectService;
    private final ApplicationSignUpService applicationSignUpService;
    private final UiDeploymentRepository uiDeploymentRepository;
    private final PasswordConfigurationService passwordConfigurationService;

    public DefaultApplicationService(ApplicationRepository repository,
                                     ProjectService projectService,
                                     ApplicationSignUpService applicationSignUpService,
                                     UiDeploymentRepository uiDeploymentRepository,
                                     PasswordConfigurationService passwordConfigurationService,
                                     SecurityContext securityContext) {
        super(repository, securityContext);
        this.projectService = projectService;
        this.applicationSignUpService = applicationSignUpService;
        this.uiDeploymentRepository = uiDeploymentRepository;
        this.passwordConfigurationService = passwordConfigurationService;
    }

    @Override
    public Future<Application> createApplicationIfNotExist(String name, String description, TenantManagement tenantManagement) {
        String applicationId = DomainUtil.slugifyId(name);
        String organizationId = requireOrganizationId();
        return findById(applicationId)
                .compose(application -> {
                    Future<Application> ret;
                    if(application != null){
                        // an existing application keeps its tenant policy: flipping it here would
                        // split its users into tenanted and untenanted halves, since only users
                        // created while it is enabled receive a tenant
                        ret = Future.succeededFuture(application);
                    }else{
                        Application newApplication = new Application(name, description);
                        newApplication.setOrganizationId(organizationId);
                        // a caller with no tenancy opinion, such as the CLI ensuring the app row exists,
                        // omits the argument and leaves tenants to their administrators
                        newApplication.setTenantManagement(tenantManagement != null ? tenantManagement : TenantManagement.TENANT_ADMINISTRATORS);
                        ret = save(newApplication);
                    }
                    return ret;
                });
    }

    @Override
    public Future<Application> create(Application entity) {
        // Force the id to derive from the name; beforeSave mints it from the slug.
        entity.setId(null);
        return failOnDuplicateName(super.create(entity), entity);
    }

    @Override
    public Future<Application> createSync(Application entity) {
        entity.setId(null);
        return failOnDuplicateName(super.createSync(entity), entity);
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
        return projectService.countForApplication(id).compose(count -> {
            if(count > 0){
                throw new IllegalStateException("Cannot delete an application with projects in it.");
            }
            // an application created again under the same id starts with no password sign-in
            String organizationId = requireOrganizationId();
            return passwordConfigurationService.deleteById(PasswordConfiguration.idOf(organizationId, id, null), organizationId);
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
            primaryUiUrl = findById(entity.getId())
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
        return findById(applicationId).compose(application -> {
            Validate.isTrue(application != null, "The organization has no application %s", applicationId);
            return applicationSignUpService.findLoginProviders(new LoginScope(application, null));
        });
    }

}
