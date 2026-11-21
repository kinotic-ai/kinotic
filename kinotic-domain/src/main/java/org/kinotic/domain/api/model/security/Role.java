package org.kinotic.domain.api.model.security;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.domain.api.model.OrganizationScoped;

import java.util.Date;

/**
 * A custom role of an organization, or of one of its applications: a name and a description for the bundle of
 * permissions the role is in the authorization engine, where every grant of it is made. The built-in roles the
 * model defines have no row.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class Role implements OrganizationScoped<String> {
    private String id;
    private String organizationId;
    /**
     * The application whose users the role is granted to, in the application's own store; null for a role of the
     * organization itself.
     */
    private String applicationId;
    /** Whether the administrators of a tenant of the application may grant the role to the users of their tenant. */
    private boolean tenantAssignable;
    private String name;
    private String description;
    private Date created;
    private Date updated;
}
