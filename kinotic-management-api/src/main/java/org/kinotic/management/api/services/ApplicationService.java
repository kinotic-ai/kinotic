package org.kinotic.management.api.services;

import io.vertx.core.Future;
import org.kinotic.core.api.annotations.Publish;
import org.kinotic.domain.api.model.Application;
import org.kinotic.domain.api.model.TenantManagement;
import org.kinotic.domain.api.model.security.LoginProviderSummary;
import org.kinotic.domain.api.services.OrganizationScopedCrudService;
import org.kinotic.idl.api.annotations.McpTool;

import java.util.List;

/**
 * Manages {@link Application}s. An application's id is derived from its slugified name at
 * creation: lowercase letters, digits, and interior dashes.
 */
@Publish
@McpTool
public interface ApplicationService extends OrganizationScopedCrudService<Application, String> {

    /**
     * Creates a new application if it does not already exist, deriving its id from the slugified name.
     * The organization id is derived from the authenticated participant.
     * @param name the name of the application to create
     * @param description the description of the application to create
     * @param tenantManagement who creates and administers the application's tenants, or null for
     *                         {@link TenantManagement#TENANT_ADMINISTRATORS}. {@code PLATFORM} applies
     *                         to users created while it is in force, so it is chosen before the
     *                         application has users
     * @return {@link Future} emitting the created application, or the existing
     *         application whose id matches the slugified name
     */
    Future<Application> createApplicationIfNotExist(String name, String description, TenantManagement tenantManagement);

    /**
     * Returns the sign-in methods the given application's login offers, in the order to present them, for
     * building the application's login page. Every application accepts email and password: the page posts
     * {@code {email, password}} to {@code POST /api/auth/app/login} on the application's API host. Each
     * provider returned here is offered as a "Continue with &lt;name&gt;" button: the page posts to
     * {@code POST /api/auth/app/login/oidc/start/<configurationId>} on the same host and sends the browser to
     * the {@code redirect} URL the answer carries; the browser returns to the page signed in, or to its
     * {@code /login} page with an {@code error} parameter. A login page never asks the server about an email
     * before the user chooses a method.
     *
     * @param applicationId the id of the application
     * @return the providers, or an empty list if the application's login offers password only; fails when
     *         the id names no application in the caller's organization
     */
    Future<List<LoginProviderSummary>> getLoginProviders(String applicationId);

}

