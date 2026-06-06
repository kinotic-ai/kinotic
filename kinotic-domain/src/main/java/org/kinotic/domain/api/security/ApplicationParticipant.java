package org.kinotic.domain.api.security;

/**
 * A participant authenticated against an Application. Extends {@link OrganizationParticipant}
 * because every Application belongs to an Organization, so APP-scope sessions intrinsically
 * carry the owning Organization's id as well.
 * <p>
 * Use {@code securityContext.requireParticipant(ApplicationParticipant.class)} to obtain
 * the current participant narrowed to this type.
 */
public interface ApplicationParticipant extends OrganizationParticipant {

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

