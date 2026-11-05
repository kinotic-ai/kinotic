package org.kinotic.domain.api.services.security;

import io.vertx.core.Future;
import org.kinotic.authz.api.model.AuthzModel;

/**
 * The authorization models the service directory implies, as it holds the services now.
 */
public interface AuthzModelService {

    /**
     * The model of the system's store, from the system's own services.
     *
     * @return the model
     */
    Future<AuthzModel> findSystemModel();

    /**
     * The model of an application's store, from the system's services its users call and the services its
     * runtimes registered.
     *
     * @param organizationId the application's organization
     * @param applicationId  the application
     * @return the model
     */
    Future<AuthzModel> findApplicationModel(String organizationId, String applicationId);

}
