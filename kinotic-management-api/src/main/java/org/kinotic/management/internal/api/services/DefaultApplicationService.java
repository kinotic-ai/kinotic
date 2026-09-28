package org.kinotic.management.internal.api.services;

import io.vertx.core.Future;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.exceptions.AlreadyExistsException;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.domain.api.model.AppHost;
import org.kinotic.domain.api.model.Application;
import org.kinotic.domain.api.model.security.OidcConfiguration;
import org.kinotic.domain.internal.api.repositories.ApplicationRepository;
import org.kinotic.domain.internal.api.services.AbstractOrganizationScopedService;
import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.management.api.repositories.UiDeploymentRepository;
import org.kinotic.management.api.services.ApplicationService;
import org.kinotic.management.api.services.ProjectService;
import org.kinotic.domain.api.services.security.OidcConfigurationService;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Date;
import java.util.List;

@Component
public class DefaultApplicationService extends AbstractOrganizationScopedService<Application> implements ApplicationService {

    private final ProjectService projectService;
    private final OidcConfigurationService oidcConfigurationService;
    private final UiDeploymentRepository uiDeploymentRepository;

    public DefaultApplicationService(ApplicationRepository repository,
                                     ProjectService projectService,
                                     OidcConfigurationService oidcConfigurationService,
                                     UiDeploymentRepository uiDeploymentRepository,
                                     SecurityContext securityContext) {
        super(repository, securityContext);
        this.projectService = projectService;
        this.oidcConfigurationService = oidcConfigurationService;
        this.uiDeploymentRepository = uiDeploymentRepository;
    }

    @Override
    public Future<Application> createApplicationIfNotExist(String name, String description, Boolean tenantPerUser) {
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
                        // omits the argument and gets the shared default
                        newApplication.setTenantPerUser(Boolean.TRUE.equals(tenantPerUser));
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
            return Future.succeededFuture();
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
        AppHost appHost = new AppHost(requireOrganizationId(), entity.getId());
        Validate.isTrue(appHost.label().length() <= AppHost.MAX_LABEL_LENGTH,
                        "The application's host label '%s' is longer than %d characters; shorten the application name",
                        appHost.label(), AppHost.MAX_LABEL_LENGTH);
        entity.setUpdated(new Date());
        Future<String> primaryUiUrl;
        if (entity.getPrimaryUiId() == null) {
            primaryUiUrl = Future.succeededFuture();
        } else {
            // resolved only when it changes, so removing the primary UI's deployment never fails the application's other edits
            primaryUiUrl = findById(entity.getId())
                    .compose(stored -> stored != null && entity.getPrimaryUiId().equals(stored.getPrimaryUiId())
                            ? Future.succeededFuture(stored.getPrimaryUiUrl())
                            : publishedUiUrl(appHost, entity.getPrimaryUiId()));
        }
        return primaryUiUrl.compose(url -> {
            entity.setPrimaryUiUrl(url);
            return Future.succeededFuture();
        });
    }

    private Future<String> publishedUiUrl(AppHost appHost, String uiName) {
        // a site's label names its application and UI, so a site with this label is one of this application's UIs
        return uiDeploymentRepository.findById(appHost.siteLabel(uiName))
                .map(site -> {
                    Validate.isTrue(site != null, "The application '%s' has no published UI named '%s'",
                                    appHost.applicationId(), uiName);
                    return site.getUrl();
                });
    }

    @Override
    public Future<List<OidcConfiguration>> getOidcConfigurations(String applicationId) {
        Validate.notNull(applicationId, "applicationId cannot be null");
        return findById(applicationId)
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
