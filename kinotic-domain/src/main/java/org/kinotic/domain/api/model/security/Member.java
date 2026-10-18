package org.kinotic.domain.api.model.security;

import org.kinotic.domain.api.model.security.identity.UserParticipantIdentity;

import java.util.Date;

/**
 * A person as a member of one scope, for listing a scope's members: the account joined with its membership
 * of an organization or of the platform, or an application's own user. {@link #enabled} is the person's
 * standing in this scope alone.
 *
 * @param id          the id of the person's {@link UserParticipantIdentity}, which member operations name
 * @param email       the person's email
 * @param displayName the person's display name, or {@code null}
 * @param enabled     whether the person may sign in to this scope
 * @param joined      when the person became a member of this scope
 */
public record Member(String id, String email, String displayName, boolean enabled, Date joined) {

    /** An application's user as a member of its application, whose standing is the user's own. */
    public static Member of(UserParticipantIdentity user) {
        return new Member(user.getId(), user.getEmail(), user.getDisplayName(), user.isEnabled(), user.getCreated());
    }
}
