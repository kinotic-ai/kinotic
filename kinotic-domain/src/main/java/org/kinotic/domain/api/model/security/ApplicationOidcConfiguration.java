package org.kinotic.domain.api.model.security;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.domain.api.model.ApplicationScoped;

/**
 * An {@link OwnedOidcConfiguration} offered at one application's login, for that application's own
 * users: a provider the organization registered with for the application, as opposed to one of the
 * platform's social registrations, which an application offers by naming the provider kind in its
 * {@code Application.socialProviders}.
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

    @Override
    public OidcConfigurationType getType() {
        return OidcConfigurationType.APPLICATION;
    }
}
