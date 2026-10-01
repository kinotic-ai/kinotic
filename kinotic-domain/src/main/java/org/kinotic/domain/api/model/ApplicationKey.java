package org.kinotic.domain.api.model;

/**
 * Identifies an application across the platform: the organization that owns it and the application's id, which
 * is unique within that organization.
 *
 * @param organizationId the organization that owns the application
 * @param applicationId  the application's id within the organization
 */
public record ApplicationKey(String organizationId, String applicationId) {
}
