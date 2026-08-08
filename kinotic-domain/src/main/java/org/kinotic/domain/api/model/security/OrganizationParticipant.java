package org.kinotic.domain.api.model.security;

/**
 * A participant authenticated against an Organization, carrying ORGANIZATION-scope authority
 * over that Organization's resources. {@link #getOrganizationId()} is the id of the owning
 * Organization and is never null. Org-scoped services read this value to filter, route, and
 * validate persistence operations on org-owned data.
 * <p>
 * Use {@code securityContext.requireParticipant(OrganizationParticipant.class)} to obtain
 * the current participant narrowed to this type.
 */
public non-sealed interface OrganizationParticipant extends ScopedParticipant {

    /**
     * @return the id of the Organization this participant is authenticated under; never null
     */
    String getOrganizationId();

    @Override
    default ParticipantScope getScope() {
        return new ParticipantScope(getOrganizationId(), null, null);
    }
}

