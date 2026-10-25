package org.kinotic.domain.api.model.security;

import org.apache.commons.lang3.Validate;
import org.kinotic.domain.api.model.Application;
import org.kinotic.domain.api.model.ApplicationKey;
import org.kinotic.domain.api.model.security.identity.ApplicationParticipantIdentity;

/**
 * The scope a sign-in at the app server is to: an application, and one of its tenants when the sign-in is
 * addressed to a tenant's site or names the tenant.
 *
 * @param application the application the sign-in is to
 * @param tenantId    the tenant of the application the sign-in is to, or {@code null} for the application itself
 */
public record LoginScope(Application application, String tenantId) {

    public LoginScope {
        Validate.notNull(application, "application is required");
    }

    /** The key of {@link #application()}. */
    public ApplicationKey applicationKey() {
        return new ApplicationKey(application.getOrganizationId(), application.getId());
    }

    /**
     * {@code user} when it is a user of this scope, else {@code null}: a sign-in to a tenant admits only the
     * tenant's users, where a sign-in to the application admits all of its.
     */
    public ApplicationParticipantIdentity memberOf(ApplicationParticipantIdentity user) {
        return user != null && (tenantId == null || tenantId.equals(user.getTenantId())) ? user : null;
    }
}
