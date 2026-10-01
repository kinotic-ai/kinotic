package org.kinotic.domain.api.model;

import org.apache.commons.lang3.Validate;

/**
 * Identifies an application across the platform: the organization that owns it and the application's id, which
 * is unique within that organization.
 *
 * @param organizationId the organization that owns the application
 * @param applicationId  the application's id within the organization
 */
public record ApplicationKey(String organizationId, String applicationId) {

    public ApplicationKey {
        Validate.notBlank(organizationId, "organizationId is required");
        Validate.notBlank(applicationId, "applicationId is required");
    }
}
