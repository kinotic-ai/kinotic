package org.kinotic.domain.api.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.core.api.crud.Identifiable;

import java.util.Date;

/**
 * Represents an organization developing applications on the Kinotic OS platform.
 * Organizations provide the boundary for teams, applications, users, and the OIDC providers
 * their sign-ins offer.
 * <p>
 * The {@code id} is auto-generated from the {@code name} on save (slugified) and serves as the URL-safe identifier.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class Organization implements Identifiable<String> {

    /**
     * The organization's slug: lowercase letters, digits and dashes, derived from {@link #name} when the
     * organization is created and fixed from then on. It is a public, URL-safe handle, so it is what a URL,
     * a login page or a person names the organization by, and no organization may ever be told apart by a
     * secret in its id.
     */
    private String id;

    private String name;

    private String description;

    /**
     * User ID of the administrator who created this organization.
     */
    private String createdBy;

    private Date created;

    private Date updated;

}
