package org.kinotic.os.api.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.core.api.crud.Identifiable;

import java.util.Date;
import java.util.List;

/**
 * Represents an organization developing applications on the Kinotic OS platform.
 * Organizations provide the boundary for teams, applications, users, and shared OIDC configuration.
 * <p>
 * The {@code slug} is auto-generated from the {@code name} on save and provides a URL-safe identifier.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class Organization implements Identifiable<String> {

    private String id;

    private String name;

    /**
     * URL-safe slug derived from the name (auto-generated on save via Slugify).
     */
    private String slug;

    private String description;

    /**
     * OIDC configuration IDs available for organization-level authentication.
     * References {@link org.kinotic.os.api.model.iam.OidcConfiguration} entities by ID.
     * The same config ID can be referenced by multiple organizations and applications.
     */
    private List<String> oidcConfigurationIds;

    /**
     * User ID of the administrator who created this organization.
     */
    private String createdBy;

    private Date created;

    private Date updated;

}
