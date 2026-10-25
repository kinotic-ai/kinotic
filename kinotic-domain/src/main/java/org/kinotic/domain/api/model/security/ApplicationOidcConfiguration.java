package org.kinotic.domain.api.model.security;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.domain.api.model.ApplicationScoped;

/**
 * An {@link OwnedOidcConfiguration} offered at one application's login, for that application's own users: a
 * sign-in provider the organization offers for the application, which signs in through Kinotic's registration
 * with the provider or the organization's own, as its {@link #clientRegistrationType} says.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public final class ApplicationOidcConfiguration extends OwnedOidcConfiguration implements ApplicationScoped<String> {

    /**
     * The application whose login offers the configuration, one of the owning organization's, which
     * the service enforces.
     */
    private String applicationId;

    /**
     * Whose registration with the provider a sign-in uses. {@link ClientRegistrationType#MANAGED} signs in
     * through Kinotic's registration for the {@link #getProvider() provider} kind, which must exist, and leaves
     * the configuration's own client id, account and secret unused but kept, so switching back to
     * {@link ClientRegistrationType#OWN} needs no new setup. A user signed in under either is the same user.
     */
    private ClientRegistrationType clientRegistrationType = ClientRegistrationType.OWN;

    @Override
    public OidcConfigurationType getType() {
        return OidcConfigurationType.APPLICATION;
    }
}
