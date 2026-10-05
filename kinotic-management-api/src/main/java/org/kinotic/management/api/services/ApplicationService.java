package org.kinotic.management.api.services;

import org.kinotic.idl.api.annotations.AuthzCheck;
import org.kinotic.idl.api.annotations.AuthzResource;
import org.kinotic.idl.api.utils.AuthzUtil;
import io.vertx.core.Future;
import org.kinotic.core.api.annotations.Publish;
import org.kinotic.core.api.crud.IdentifiableCrudService;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.Application;
import org.kinotic.domain.api.model.OnboardingMechanism;
import org.kinotic.domain.api.model.security.OidcConfiguration;
import org.kinotic.idl.api.annotations.McpTool;

import java.util.List;
import java.util.Set;

/**
 * Manages {@link Application}s. An application's id is derived from its slugified name at
 * creation: lowercase letters, digits, and interior dashes.
 */
// FIXME: add an OrganizationScopedServiceInterface
@Publish
@McpTool
@AuthzResource(value = AuthzUtil.APPLICATION_TYPE, parent = AuthzUtil.ORGANIZATION_TYPE)
public interface ApplicationService extends IdentifiableCrudService<Application, String> {

    /**
     * Returns the application with the given id when the caller may see it: an application the caller may view,
     * or one containing a project or an entity definition the caller may view.
     *
     * @param id the application's id
     * @return a {@link Future} emitting the application, or null when none has the id or the caller may not see it
     */
    @AuthzCheck(zoneOnly = true)
    Future<Application> findById(String id);

    /**
     * Returns the number of applications the caller may see: those the caller may view, and those containing a
     * project or an entity definition the caller may view.
     *
     * @return a {@link Future} emitting the count
     */
    @AuthzCheck(zoneOnly = true)
    Future<Long> count();

    /**
     * Returns a page of the applications the caller may see: those the caller may view, and those containing a
     * project or an entity definition the caller may view.
     *
     * @param pageable the page to return
     * @return a {@link Future} emitting the page
     */
    @AuthzCheck(zoneOnly = true)
    Future<Page<Application>> findAll(Pageable pageable);

    /**
     * Searches the applications the caller may see: those the caller may view, and those containing a project
     * or an entity definition the caller may view.
     *
     * @param searchText the text to search for
     * @param pageable   the page to return
     * @return a {@link Future} emitting the matching page
     */
    @AuthzCheck(zoneOnly = true)
    Future<Page<Application>> search(String searchText, Pageable pageable);

    /**
     * Creates a new application if it does not already exist, deriving its id from the slugified name.
     * The organization id is derived from the authenticated participant.
     * @param name the name of the application to create
     * @param description the description of the application to create
     * @param onboarding the ways a user comes to belong to a tenant of the application, or null for none, which
     *                   leaves every user sharing one tenant-less set of data; {@code TENANT_PER_USER} applies to
     *                   users created while it is enabled, so it is chosen before the application has users, and
     *                   excludes the other mechanisms
     * @return {@link Future} emitting the created application, or the existing
     *         application whose id matches the slugified name
     */
    Future<Application> createApplicationIfNotExist(String name, String description, Set<OnboardingMechanism> onboarding);

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

