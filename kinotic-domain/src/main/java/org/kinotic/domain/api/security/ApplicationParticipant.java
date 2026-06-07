package org.kinotic.domain.api.security;

import org.kinotic.core.api.security.Participant;

/**
 * A participant authenticated against an Application. {@link #getOrganizationId()} is the id
 * of the Organization that owns the Application; it identifies the owning org for data
 * ownership and routing of the Application's entity definitions and end-user data, and is
 * never null.
 * <p>
 * This type is deliberately not an {@link OrganizationParticipant}: holding the owning org's
 * id does not confer org-scoped authority, so an application participant cannot be used where
 * an {@code OrganizationParticipant} is required (it will not satisfy
 * {@code requireParticipant(OrganizationParticipant.class)}).
 * <p>
 * Use {@code securityContext.requireParticipant(ApplicationParticipant.class)} to obtain
 * the current participant narrowed to this type.
 */
public interface ApplicationParticipant extends Participant {

    /**
     * @return the id of the Organization that owns the Application this participant is
     *         authenticated under; never null
     */
    String getOrganizationId();

    /**
     * @return the id of the Application this participant is authenticated under; never null
     */
    String getApplicationId();

    /**
     * @return the tenant slice of the Application's end-user data this participant belongs
     *         to, or null when the Application is not multi-tenant
     */
    String getTenantId();
}
