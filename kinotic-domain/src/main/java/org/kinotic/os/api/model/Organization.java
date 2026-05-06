package org.kinotic.os.api.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.core.api.crud.Identifiable;

import java.util.Date;

/**
 * Represents an organization developing applications on the Kinotic OS platform.
 * Organizations provide the boundary for teams, applications, users, and the
 * organization's single SSO/IdP configuration.
 * <p>
 * The {@code id} is auto-generated from the {@code name} on save (slugified) and serves as the URL-safe identifier.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class Organization implements Identifiable<String> {

    private String id;

    private String name;

    private String description;

    /**
     * Id of the {@link org.kinotic.os.api.model.iam.OidcConfiguration} this organization
     * uses as its SSO provider for org-level Kinotic login. {@code null} when the org
     * has no SSO configured (members log in via password). Structurally enforces
     * "one SSO config per org" — there's just the one field.
     *
     * <p>The same config id can also appear in one or more of the org's apps' OIDC lists
     * if the admin wants to reuse the same IdP for application logins; that's a separate
     * association and uses {@link Application#getOidcConfigurationIds()}.
     */
    private String ssoConfigId;

    /**
     * User ID of the administrator who created this organization.
     */
    private String createdBy;

    private Date created;

    private Date updated;

}
