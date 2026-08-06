package org.kinotic.domain.api.model.security;

/**
 * A participant authenticated against an Application, carrying APPLICATION-scope authority over
 * that Application's own resources and end-user data. {@link #getOrganizationId()} is the id
 * of the Organization that owns the Application; it identifies the owning org for data
 * ownership and routing of the Application's entity definitions and end-user data, and is
 * never null.
 * <p>
 * Use {@code securityContext.requireParticipant(ApplicationParticipant.class)} to obtain
 * the current participant narrowed to this type.
 */
public non-sealed interface ApplicationParticipant extends ScopedParticipant {

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

    @Override
    default ParticipantScope getScope() {
        return new ParticipantScope(getOrganizationId(), getApplicationId(), getTenantId());
    }
}
