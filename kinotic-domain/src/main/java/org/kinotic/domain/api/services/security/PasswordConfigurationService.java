package org.kinotic.domain.api.services.security;

import io.vertx.core.Future;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.security.PasswordConfiguration;

/**
 * The {@link PasswordConfiguration}s of organizations' sign-in scopes, which decide where an email and a password sign
 * in: a login takes a password only while a configuration of its scope offers it.
 */
public interface PasswordConfigurationService {

    /**
     * The enabled configuration the login of a scope offers, or {@code null} when the login takes no password: the
     * organization's own login offers its configuration, an application's login its own, and a tenant's login the
     * tenant's, else the application's. Checks no participant.
     *
     * @param organizationId the organization the scope belongs to
     * @param applicationId  the application within it, or {@code null} for the organization's own login
     * @param tenantId       the tenant of the application, or {@code null} for the application's own login
     */
    Future<PasswordConfiguration> findOffered(String organizationId, String applicationId, String tenantId);

    /**
     * The configuration {@code id} of organization {@code organizationId}, enabled or not, or {@code null} when the
     * organization has none by that id. Checks no participant.
     */
    Future<PasswordConfiguration> findById(String id, String organizationId);

    /** The configurations of organization {@code organizationId}, of every scope, a page at a time. Checks no participant. */
    Future<Page<PasswordConfiguration>> findByOrganization(String organizationId, Pageable pageable);

    /**
     * Saves the configuration of the scope it names, replacing the scope's existing one; its id is derived from the
     * scope, whatever it carried. The bound participant must be one of the configuration's organization, and the
     * application, and the tenant, it names must be the organization's. {@code createUserOnSignIn} is refused on an
     * organization's or a tenant's configuration, and on an application's whose tenants the system does not manage.
     * Disabling an organization's own configuration fails with an {@link IllegalStateException} naming the enabled
     * members it would leave with no way to sign in to the organization.
     *
     * @return the configuration as saved
     */
    Future<PasswordConfiguration> save(PasswordConfiguration configuration);

    /**
     * Deletes organization {@code organizationId}'s configuration {@code id}, so its scope's login takes no password,
     * refused as disabling it is in {@link #save}. Nothing happens for an id the organization has no configuration
     * by. The bound participant must be one of the organization.
     */
    Future<Void> deleteById(String id, String organizationId);
}
