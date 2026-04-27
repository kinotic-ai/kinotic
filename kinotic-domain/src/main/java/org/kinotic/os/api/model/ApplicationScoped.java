package org.kinotic.os.api.model;

/**
 * Marker interface for entities that belong to an application within an organization.
 * Extends {@link OrganizationScoped} so the entity also carries the organization id.
 * <p>
 * Entities implementing this interface will be automatically filtered by applicationId
 * (and organizationId) in {@code AbstractApplicationCrudService}.
 */
public interface ApplicationScoped<T> extends OrganizationScoped<T> {

    String getApplicationId();

}
