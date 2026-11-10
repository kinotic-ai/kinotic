package org.kinotic.domain.api.services.security;

import io.vertx.core.Future;
import org.kinotic.authz.api.model.AuthzModel;
import org.kinotic.idl.api.schema.ServiceDefinition;

import java.util.Collection;

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

    /**
     * The model an application's store would run once {@code candidates} are registered, each standing in for
     * the service of the same qualified name the directory holds now. The future fails with an
     * {@link IllegalArgumentException} when no model can be generated from the candidates' declarations.
     *
     * @param organizationId the application's organization
     * @param applicationId  the application
     * @param candidates     the converted contracts of the application's services about to be registered
     * @return the model
     */
    Future<AuthzModel> findApplicationModel(String organizationId, String applicationId, Collection<ServiceDefinition> candidates);

}
