package org.kinotic.management.api.services.security;

import io.vertx.core.Future;
import org.kinotic.core.api.annotations.Publish;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.security.OidcProviderType;
import org.kinotic.domain.api.model.security.OwnedOidcConfiguration;
import org.kinotic.idl.api.annotations.McpTool;

import java.util.List;

/**
 * The caller's organization's own OIDC configurations ({@link OwnedOidcConfiguration}): the providers it offers at
 * its own login, at one of its applications' and at one tenant's, each a configuration of that scope's kind. Every
 * operation acts for the organization of the authenticated participant and reaches no other organization's
 * configurations. A configuration's client secret is given on save and held in the organization's scope of the
 * secret storage, where it stays until a save gives another or {@link #deleteClientSecret(String)} deletes it;
 * nothing here reads it back.
 */
@Publish
@McpTool
public interface OwnedOidcConfigurationService {

    /** The organization's configurations, of every kind and enabled or not, oldest first, a page at a time. */
    Future<Page<OwnedOidcConfiguration>> findAll(Pageable pageable);

    /**
     * The provider kinds an application's configuration may sign in through Kinotic's registration for, by setting its
     * {@code clientRegistrationType} to {@code MANAGED}: no client of the organization's own is needed, and the
     * provider's consent screen shows Kinotic's name. Any kind offered to organizations may use the application's own
     * registration instead.
     */
    Future<List<OidcProviderType>> findManagedProviders();

    /** The organization's configuration {@code id}, or {@code null} when the organization owns none by that id. */
    Future<OwnedOidcConfiguration> findById(String id);

    /**
     * The URL the provider of the organization's configuration {@code id} must be given as its redirect URI before
     * a sign-in through the configuration succeeds: the callback of the organization's login for an organization's
     * configuration, and the callback on the application's own API host for an application's or a tenant's. Every
     * flow through the configuration returns to it. {@code null} when the organization owns no configuration by
     * that id.
     */
    Future<String> findCallbackUrl(String id);

    /**
     * Saves a configuration of the organization, new or changed, and makes {@code clientSecret}, when given, the
     * secret it sends its provider. The configuration names a provider kind offered to organizations and the
     * organization's account there in the form the kind asks for, from which its authority is derived on every
     * save; one for an application or a tenant names one of the organization's. An application's configuration
     * whose {@code clientRegistrationType} is {@code MANAGED} names one of {@link #findManagedProviders()} and needs
     * no account, client id or secret; any it holds are kept for a switch back to {@code OWN}, under which the
     * application's users are the same users. Its {@code organizationId} is the
     * caller's, whatever it carried, and its {@code confidentialClient} says whether a secret is held, whatever it
     * carried. A sign-in through the configuration picks a new secret up on its next flow.
     *
     * @param configuration the configuration
     * @param clientSecret  the client secret's value, or {@code null} or blank to keep the secret held for the
     *                      configuration, if any; a new configuration saved without one is a public client
     * @return the configuration as saved, with its id
     */
    Future<OwnedOidcConfiguration> save(OwnedOidcConfiguration configuration, String clientSecret);

    /**
     * Deletes the client secret held for the organization's configuration {@code id}, which from then on is a
     * public client, sending its provider no secret on the code exchange.
     *
     * @return the configuration as saved, or {@code null} when the organization owns no configuration by that id
     */
    Future<OwnedOidcConfiguration> deleteClientSecret(String id);

    /**
     * Creates a copy of the organization's configuration {@code sourceId} for another of its sign-in scopes: the
     * login of application {@code applicationId}, or the organization's own login when it is null. The copy
     * carries the source's name, provider, account, client id and client secret, starts enabled and creates no
     * users on sign-in until told to, and has an id, and so a callback URL, of its own, which the provider must
     * be given before a sign-in through it succeeds.
     *
     * @return the copy as saved
     */
    Future<OwnedOidcConfiguration> copy(String sourceId, String applicationId);

    /** Deletes the organization's configuration {@code id} and the client secret held for it; nothing happens for an id the organization owns no configuration by. */
    Future<Void> deleteById(String id);

}
