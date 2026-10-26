package org.kinotic.domain.api.model.security;

import org.kinotic.domain.api.model.security.identity.OrganizationParticipantIdentity;

import java.util.Date;

/**
 * A member of one organization, for listing the organization's members: an {@link OrganizationParticipantIdentity}
 * with whether anyone signs in as it yet.
 *
 * @param id          the id of the member's user, which member operations name
 * @param email       the member's email
 * @param displayName the member's display name, or {@code null}
 * @param enabled     whether the member may sign in to the organization
 * @param joined      when the member was added
 * @param pending     whether the member has yet to sign in for the first time: an administrator added them and no
 *                    sign-in method claims the user yet, so the invitation they were sent, or the organization's own
 *                    provider, is what admits them
 */
public record OrganizationMember(String id, String email, String displayName, boolean enabled, Date joined, boolean pending) {
}
