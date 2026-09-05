package org.kinotic.domain.api.model.security;

import org.kinotic.domain.api.model.Application;
import org.kinotic.domain.api.model.Organization;
import org.kinotic.domain.api.model.OrganizationScoped;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * An OIDC provider configuration owned by an {@link Organization}.
 * "Where can this config be used" is expressed by inbound references rather than a flag
 * on the row itself:
 * <ul>
 *   <li>{@link Organization#getSsoConfigId()} points at the
 *       org's single SSO config (when set).</li>
 *   <li>{@link Application#getOidcConfigurationIds()} lists the
 *       configs each application accepts for application-level login.</li>
 * </ul>
 * The same config id may legitimately appear in both — e.g. an org uses the same Okta
 * tenant for org-admin SSO and for one of its customer-facing apps.
 *
 * <p>Kinotic-curated social configs (Google, Microsoft Live, etc.) live separately in
 * {@link OrgSignupOidcConfiguration} — kept apart so the authorization paths and
 * lifecycle (admin UI vs. seeded migration) don't collide.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class OidcConfiguration extends BaseOidcConfiguration implements OrganizationScoped<String> {

    /**
     * Owning organization. Auto-populated and enforced by {@code AbstractCrudService}
     * from the security context — callers don't set it directly outside elevated access.
     */
    private String organizationId;
}
