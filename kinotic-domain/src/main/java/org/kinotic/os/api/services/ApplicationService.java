package org.kinotic.os.api.services;

import org.kinotic.core.api.annotations.Publish;
import org.kinotic.core.api.crud.IdentifiableCrudService;
import org.kinotic.os.api.model.Application;
import org.kinotic.os.api.model.iam.OidcConfiguration;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Publish
public interface ApplicationService extends IdentifiableCrudService<Application, String> {

    /**
     * Creates a new application if it does not already exist. The organization id is derived
     * from the authenticated participant.
     * @param id the id of the application to create
     * @param description the description of the application to create
     * @return {@link CompletableFuture} emitting the created application
     */
    CompletableFuture<Application> createApplicationIfNotExist(String id, String description);

    /**
     * Returns the enabled OIDC configurations registered on the given application.
     *
     * @param applicationId the id of the application
     * @return the enabled configurations, or an empty list if the application is not
     *         found or has no configurations attached
     */
    CompletableFuture<List<OidcConfiguration>> getOidcConfigurations(String applicationId);

}

