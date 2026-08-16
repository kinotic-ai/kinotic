package org.kinotic.os.api.services;

import io.vertx.core.Future;
import org.kinotic.core.api.annotations.Publish;
import org.kinotic.core.api.crud.IdentifiableCrudService;
import org.kinotic.domain.api.model.Application;
import org.kinotic.domain.api.model.security.OidcConfiguration;
import org.kinotic.idl.api.annotations.McpTool;

import java.util.List;

/**
 * Manages {@link Application}s. An application's id is derived from its slugified name at
 * creation: lowercase letters, digits, and interior dashes.
 */
// FIXME: add an OrganizationScopedServiceInterface
@Publish
@McpTool
public interface ApplicationService extends IdentifiableCrudService<Application, String> {

    /**
     * Creates a new application if it does not already exist, deriving its id from the slugified name.
     * The organization id is derived from the authenticated participant.
     * @param name the name of the application to create
     * @param description the description of the application to create
     * @return {@link Future} emitting the created application, or the existing
     *         application whose id matches the slugified name
     */
    Future<Application> createApplicationIfNotExist(String name, String description);

    /**
     * Returns the enabled OIDC configurations registered on the given application.
     *
     * @param applicationId the id of the application
     * @return the enabled configurations, or an empty list if the application has no
     *         configurations attached; fails when the id names no application in the
     *         caller's organization
     */
    Future<List<OidcConfiguration>> getOidcConfigurations(String applicationId);

}

