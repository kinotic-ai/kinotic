package org.kinotic.domain.api.model.security;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.domain.api.model.ApplicationScoped;
import org.kinotic.domain.api.model.Tenant;

/**
 * An {@link OwnedOidcConfiguration} offered at the login of one {@link Tenant} of one of the organization's
 * applications, for that tenant's users: the tenant's own identity provider. With {@link #isCreateUserOnSignIn()}
 * on, an identity the provider verifies becomes a user of the tenant on its first sign-in; a configuration of
 * the application itself never creates a user into a tenant.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public final class TenantOidcConfiguration extends OwnedOidcConfiguration implements ApplicationScoped<String> {

    /** The application the tenant belongs to, one of the owning organization's, which the service enforces. */
    private String applicationId;

    /** The tenant whose login offers the configuration, one of the application's, which the service enforces. */
    private String tenantId;

    @Override
    public OidcConfigurationType getType() {
        return OidcConfigurationType.TENANT;
    }
}
