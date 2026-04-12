package org.kinotic.os.internal.api.services;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import org.apache.commons.lang3.Validate;
import org.kinotic.os.api.model.Application;
import org.kinotic.os.api.model.iam.OidcConfiguration;
import org.kinotic.os.api.services.ApplicationService;
import org.kinotic.os.api.services.ProjectService;
import org.kinotic.os.api.services.iam.OidcConfigurationService;
import org.kinotic.os.api.utils.DomainUtil;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Component
public class DefaultApplicationService extends AbstractCrudService<Application> implements ApplicationService {

    private final ProjectService projectService;
    private final OidcConfigurationService oidcConfigurationService;

    public DefaultApplicationService(ElasticsearchAsyncClient esAsyncClient,
                                     ProjectService projectService,
                                     OidcConfigurationService oidcConfigurationService,
                                     CrudServiceTemplate crudServiceTemplate) {
        super("kinotic_application",
              Application.class,
              esAsyncClient,
              crudServiceTemplate);
        this.projectService = projectService;
        this.oidcConfigurationService = oidcConfigurationService;
    }

    @Override
    public CompletableFuture<Application> createApplicationIfNotExist(String id, String description) {
        DomainUtil.validateApplicationId(id);
        return findById(id)
                .thenCompose(application -> {
                    if(application != null){
                        return CompletableFuture.completedFuture(application);
                    }else{
                        return save(new Application(id, description));
                    }
                });
    }

    @Override
    public CompletableFuture<Void> deleteById(String id) {
        return projectService.countForApplication(id).thenAccept(count -> {
            if(count > 0){
                throw new IllegalStateException("Cannot delete an application with projects in it.");
            }
        }).thenCompose(v -> super.deleteById(id));
    }

    @Override
    public CompletableFuture<Application> save(Application entity) {
        DomainUtil.validateApplicationId(entity.getId());
        entity.setUpdated(new Date());
        return super.save(entity);
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
                    return oidcConfigurationService.findEnabledByIds(ids);
                });
    }

}
