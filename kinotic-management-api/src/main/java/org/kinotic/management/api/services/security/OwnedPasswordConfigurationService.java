package org.kinotic.management.api.services.security;

import io.vertx.core.Future;
import org.kinotic.core.api.annotations.Publish;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.security.PasswordConfiguration;
import org.kinotic.idl.api.annotations.McpTool;

/**
 * The caller's organization's {@link PasswordConfiguration}s: sign-in with an email and a password at its own login,
 * at one of its applications' and at one tenant's, each managed among the organization's sign-in providers beside its
 * OIDC configurations. A login takes a password only while a configuration of its scope is enabled, and a tenant's
 * login also while its application's is. Every operation acts for the organization of the authenticated participant
 * and reaches no other organization's configurations.
 */
@Publish
@McpTool
public interface OwnedPasswordConfigurationService {

    /** The organization's configurations, of every scope and enabled or not, a page at a time. */
    Future<Page<PasswordConfiguration>> findAll(Pageable pageable);

    /**
     * Saves the configuration of the sign-in scope it names, replacing that scope's existing one: the organization's
     * own login when {@code applicationId} is null, the application's login when {@code tenantId} is null, else the
     * tenant's. Its {@code organizationId} is the caller's and its id is derived from the scope, whatever either
     * carried. {@code createUserOnSignIn}, which lets new people sign up with an email and a password, is taken by an
     * application's own configuration only, and only while the system manages the application's tenants, which gives
     * each new user a tenant of its own.
     * Disabling the organization's own configuration fails, naming the members it would lock out, while an enabled
     * member holds a password and no identity of a provider the organization's login offers.
     *
     * @return the configuration as saved, with its id
     */
    Future<PasswordConfiguration> save(PasswordConfiguration configuration);

    /**
     * Deletes the organization's configuration {@code id}, so its scope's login takes no password, refused as disabling
     * it is by {@link #save}; nothing happens for an id the organization has no configuration by.
     */
    Future<Void> deleteById(String id);

}
