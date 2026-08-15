package org.kinotic.domain.internal.api.repositories;

import io.vertx.core.Future;
import org.apache.commons.lang3.Validate;
import org.kinotic.domain.api.model.Application;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.springframework.stereotype.Component;

@Component
public class ApplicationRepository extends AbstractOrganizationScopedRepository<Application> {

    public ApplicationRepository(CrudServiceTemplate crudServiceTemplate) {
        super("kinotic_application", Application.class, crudServiceTemplate);
    }

    /**
     * Loads the application, failing when it does not exist in the given organization. The
     * guard for write paths that persist a reference to an application — reads are protected
     * by scope-composed queries instead, which simply miss for a foreign application.
     *
     * @param applicationId the application that must exist
     * @param organizationId the organization it must belong to
     * @return a future emitting the application, failed when it is not in the organization
     */
    public Future<Application> requireById(String applicationId, String organizationId) {
        Validate.notBlank(applicationId, "applicationId cannot be blank");
        return findById(applicationId, organizationId)
                .map(app -> {
                    if (app == null) {
                        throw new IllegalArgumentException("Application not found: " + applicationId);
                    }
                    return app;
                });
    }
}
