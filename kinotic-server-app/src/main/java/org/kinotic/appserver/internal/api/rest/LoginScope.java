package org.kinotic.appserver.internal.api.rest;

import org.kinotic.domain.api.model.ApplicationKey;
import org.kinotic.domain.api.model.security.identity.UserParticipantIdentity;

/**
 * The scope a sign-in at the app server is to: an application, and one of its tenants when the sign-in is
 * addressed to a tenant's site or names the tenant.
 *
 * @param application the application the sign-in is to
 * @param tenantId    the tenant of the application the sign-in is to, or {@code null} for the application itself
 */
public record LoginScope(ApplicationKey application, String tenantId) {

    /**
     * {@code user} when it is a user of this scope, else {@code null}: a sign-in to a tenant admits only the
     * tenant's users, where a sign-in to the application admits all of its.
     */
    public UserParticipantIdentity memberOf(UserParticipantIdentity user) {
        return user != null && (tenantId == null || tenantId.equals(user.getTenantId())) ? user : null;
    }
}
