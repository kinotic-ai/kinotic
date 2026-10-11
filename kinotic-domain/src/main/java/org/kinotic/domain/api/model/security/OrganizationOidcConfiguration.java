package org.kinotic.domain.api.model.security;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.domain.api.model.Application;
import org.kinotic.domain.api.model.Organization;
import org.kinotic.domain.api.model.OrganizationScoped;

/**
 * An OIDC provider configuration owned by an {@link Organization}, saved by that organization
 * alone. "Where can this config be used" is expressed by inbound references rather than a flag
 * on the row itself:
 * <ul>
 *   <li>{@link Organization#getSsoConfigId()} points at the org's single SSO config (when set).</li>
 *   <li>{@link Application#getOidcConfigurationIds()} lists the configs each application accepts
 *       for application-level login.</li>
 * </ul>
 * The same config id may legitimately appear in both — e.g. an org uses the same Okta tenant for
 * org-admin SSO and for one of its customer-facing apps.
 *
 * <p>The client secret is given to {@code OidcConfigurationService.save(configuration, clientSecret)}
 * and held in the owning organization's scope of the secret storage, keyed by the configuration's
 * id; nothing on the configuration names it, so a configuration read back carries no trace of it.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public final class OrganizationOidcConfiguration extends OidcConfiguration implements OrganizationScoped<String> {

    /**
     * Owning organization: the one of the participant saving the configuration, which the service
     * enforces.
     */
    private String organizationId;

    @Override
    public OidcConfigurationType getType() {
        return OidcConfigurationType.ORGANIZATION;
    }
}
