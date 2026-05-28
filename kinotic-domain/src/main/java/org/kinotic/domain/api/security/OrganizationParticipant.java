package org.kinotic.domain.api.security;

import org.kinotic.core.api.security.Participant;

/**
 * A participant authenticated against an Organization. {@link #getOrganizationId()} is the
 * id of the owning Organization and is never null. Org-scoped services read this value to
 * filter, route, and validate persistence operations on org-owned data.
 */
public interface OrganizationParticipant extends Participant {

    /**
     * @return the id of the Organization this participant is authenticated under; never null
     */
    String getOrganizationId();
}
