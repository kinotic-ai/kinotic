package org.kinotic.os.internal.api.services;

import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.exceptions.AlreadyExistsException;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.domain.api.model.Application;
import org.kinotic.domain.api.model.iam.OidcConfiguration;
import org.kinotic.domain.internal.api.repositories.ApplicationRepository;
import org.kinotic.domain.internal.api.services.AbstractOrganizationScopedService;
import org.kinotic.domain.internal.utils.DomainUtil;
import org.kinotic.os.api.services.ApplicationService;
import org.kinotic.os.api.services.ProjectService;
import org.kinotic.domain.api.services.iam.OidcConfigurationService;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Component
public class DefaultApplicationService extends AbstractOrganizationScopedService<Application> implements ApplicationService {

    private final ProjectService projectService;
    private final OidcConfigurationService oidcConfigurationService;

    public DefaultApplicationService(ApplicationRepository repository,
                                     ProjectService projectService,
                                     OidcConfigurationService oidcConfigurationService,
                                     SecurityContext securityContext) {
        super(repository, securityContext);
        this.projectService = projectService;
        this.oidcConfigurationService = oidcConfigurationService;
    }

    @Override
    public CompletableFuture<Application> createApplicationIfNotExist(String name, String description) {
        String applicationId = DomainUtil.slugifyId(name);
        String organizationId = requireOrganizationId();
        return findById(applicationId)
                .thenCompose(application -> {
                    if(application != null){
                        return CompletableFuture.completedFuture(application);
                    }else{
                        Application newApplication = new Application(name, description);
                        newApplication.setOrganizationId(organizationId);
                        return save(newApplication);
                    }
                });
    }

    @Override
    public CompletableFuture<Application> create(Application entity) {
        // Force the id to derive from the name; beforeSave mints it from the slug.
        entity.setId(null);
        return failOnDuplicateName(super.create(entity), entity);
    }

    @Override
    public CompletableFuture<Application> createSync(Application entity) {
        entity.setId(null);
        return failOnDuplicateName(super.createSync(entity), entity);
    }

    // The caller supplied a name, not the derived id an AlreadyExistsException would reference
    private static CompletableFuture<Application> failOnDuplicateName(CompletableFuture<Application> created,
                                                                      Application entity) {
        return created.exceptionallyCompose(ex -> AlreadyExistsException.isCause(ex)
                ? CompletableFuture.failedFuture(new AlreadyExistsException(
                        "An application named '" + entity.getName() + "' already exists"))
                : CompletableFuture.failedFuture(ex));
    }

    @Override
    protected CompletableFuture<Void> beforeDelete(String id) {
        return projectService.countForApplication(id).thenAccept(count -> {
            if(count > 0){
                throw new IllegalStateException("Cannot delete an application with projects in it.");
            }
        });
    }

    @Override
    protected CompletableFuture<Void> beforeSave(Application entity) {
        Validate.notNull(entity.getName(), "Application name cannot be null");

        if (entity.getId() == null) {
            entity.setId(DomainUtil.slugifyId(entity.getName()));
        }
        // Validate only; re-minting an update's id would silently write a new document
        DomainUtil.validateApplicationId(entity.getId());
        entity.setUpdated(new Date());
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<List<OidcConfiguration>> getOidcConfigurations(String applicationId) {
        Validate.notNull(applicationId, "applicationId cannot be null");
        return findById(applicationId)
                .thenCompose(application -> {
                    Validate.notNull(application, "Application not found: %s", applicationId);
                    List<String> ids = application.getOidcConfigurationIds();
                    if (ids == null || ids.isEmpty()) {
                        return CompletableFuture.completedFuture(Collections.emptyList());
                    }
                    return oidcConfigurationService.findEnabledByIds(ids, application.getOrganizationId());
                });
    }

}
