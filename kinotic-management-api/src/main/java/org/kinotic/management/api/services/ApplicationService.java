package org.kinotic.management.api.services;

import io.vertx.core.Future;
import org.kinotic.core.api.annotations.Publish;
import org.kinotic.domain.api.model.Application;
import org.kinotic.domain.api.model.TenantManagement;
import org.kinotic.domain.api.model.security.ApplicationLoginProvider;
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
     *                         {@link TenantManagement#TENANT_ADMINISTRATORS}. {@code SYSTEM} applies
     *                         to users created while it is in force, so it is chosen before the
     *                         application has users; it is the one under which new people may sign up
     *                         at the application's own login
     * @return {@link Future} emitting the created application, or the existing
     *         application whose id matches the slugified name
     */
    Future<Application> createApplicationIfNotExist(String name, String description, TenantManagement tenantManagement);

    /**
     * Returns the sign-in providers the given application's login offers, in the order to present them, for building
     * the application's sign-in and sign-up pages, which every page that offers them does through the application's API
     * host: {@code GET /api/auth/app/login/methods} answers the same for the page that asks. A provider of type
     * {@code PASSWORD} is an email and a password: the sign-in page shows a form that posts {@code {email, password}}
     * to {@code POST /api/auth/app/login}, and while its {@code signUp} is true the sign-up page posts {@code {email}}
     * to {@code POST /api/auth/app/signup}, the person follows the emailed link to the UI's
     * {@code /signup/verify?token=} page, and that page posts {@code {token, password[, displayName]}} to
     * {@code POST /api/auth/app/signup/complete}, which signs the person in. A provider of type {@code OIDC} is offered
     * as a "Continue with &lt;name&gt;" button on both pages: the page posts to
     * {@code POST /api/auth/app/login/oidc/start/<configurationId>} and sends the browser to the {@code redirect} URL
     * the answer carries; the browser returns to the page signed in, or to its {@code /login} page with an
     * {@code error} parameter, and one whose {@code signUp} is true creates the user of a person it verifies on that
     * person's first sign-in. A page never asks the server about an email before the user chooses a method.
     *
     * @param applicationId the id of the application
     * @return the providers; fails when the id names no application in the caller's organization
     */
    Future<List<ApplicationLoginProvider>> getLoginMethods(String applicationId);

}
