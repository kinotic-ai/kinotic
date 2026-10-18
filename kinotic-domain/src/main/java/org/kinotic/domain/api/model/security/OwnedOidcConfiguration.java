package org.kinotic.domain.api.model.security;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.domain.api.model.Organization;
import org.kinotic.domain.api.model.OrganizationScoped;

/**
 * An OIDC provider configuration an {@link Organization} made for exactly one of its sign-in scopes,
 * saved by that organization alone. The concrete subtype is the scope: an
 * {@link OrganizationOidcConfiguration} is offered at the organization's own login, an
 * {@link ApplicationOidcConfiguration} at one of its applications' logins, a {@link TenantOidcConfiguration}
 * at the login of one tenant of one of its applications. A configuration is offered
 * nowhere else; an organization that wants the same provider at a second scope copies the
 * configuration into that scope, and each copy is registered with the provider under its own
 * callback URL.
 *
 * <p>The organization names no URL. It chooses a {@link OidcProviderKind} that
 * {@link OidcProviderKind#offeredToOrganizations() is offered to organizations} and gives its
 * {@link #providerAccount} at that provider; the authority is derived from the kind's template when the
 * configuration is saved, replacing any value sent, so the server connects to the provider's own hosts
 * only.
 *
 * <p>The client secret is given to {@code OidcConfigurationService.save(configuration, clientSecret)}
 * and held in the owning organization's scope of the secret storage, keyed by the configuration's
 * id; nothing on the configuration names it, so a configuration read back carries only
 * {@link #confidentialClient}, which says whether one is held.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public abstract sealed class OwnedOidcConfiguration extends OidcConfiguration implements OrganizationScoped<String>
        permits OrganizationOidcConfiguration, ApplicationOidcConfiguration, TenantOidcConfiguration {

    /**
     * Owning organization: the one of the participant saving the configuration, which the service
     * enforces.
     */
    private String organizationId;

    /**
     * The organization's account at the provider, in the form
     * {@link OidcProviderKind#providerAccountLabel()} asks for: an Entra directory's tenant id, an Okta
     * domain. {@code null} for a kind with one issuer for everyone, such as Google.
     */
    private String providerAccount;

    /**
     * Whether a client secret is held for this configuration and sent on the code exchange, which
     * OAuth 2.0 calls a confidential client; {@code false} for a public client. Set by
     * {@code OidcConfigurationService.save(configuration, clientSecret)} from whether a secret was
     * given, and kept as it is by a save of the configuration alone.
     */
    private boolean confidentialClient;

    /**
     * Whether an identity the provider verifies that no user of the scope is bound to becomes a user
     * of the scope on its first sign-in, created from the provider's verified email and name and
     * bound to the identity. Off, such an identity is refused and a user must be invited or created
     * first. An identity whose email the scope already holds under another method is refused either
     * way.
     */
    private boolean createUserOnSignIn;
}
