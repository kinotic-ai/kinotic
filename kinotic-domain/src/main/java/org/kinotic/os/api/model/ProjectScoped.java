package org.kinotic.os.api.model;

/**
 * Marker interface for entities that belong to a project within an application and organization.
 * Extends {@link ApplicationScoped} so the entity also carries the application and organization ids.
 * <p>
 * Entities implementing this interface will be automatically filtered by projectId
 * (and applicationId and organizationId) in {@code AbstractProjectCrudService}.
 */
public interface ProjectScoped<T> extends ApplicationScoped<T> {

    String getProjectId();

    void setProjectId(String projectId);

}
