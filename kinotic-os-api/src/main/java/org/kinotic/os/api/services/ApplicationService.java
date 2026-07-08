package org.kinotic.os.api.services;

import org.kinotic.core.api.annotations.Publish;
import org.kinotic.core.api.crud.IdentifiableCrudService;
import org.kinotic.domain.api.model.Application;
import org.kinotic.domain.api.model.iam.OidcConfiguration;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Manages {@link Application}s. An application's id forms the final label of its zone,
 * {@code app.<organizationId>.<applicationId>}, so creation normalizes the given id to a
 * zone-safe slug of lowercase letters, digits, and interior dashes or underscores.
 */
// FIXME: add an OrganizationScopedServiceInterface
@Publish
public interface ApplicationService extends IdentifiableCrudService<Application, String> {

    /**
     * Creates a new application if it does not already exist. The id is normalized to a
     * zone-safe slug; the returned application carries the resulting id. The organization id
     * is derived from the authenticated participant.
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

