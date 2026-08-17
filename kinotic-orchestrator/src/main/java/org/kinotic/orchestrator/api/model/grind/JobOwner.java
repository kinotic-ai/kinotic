package org.kinotic.orchestrator.api.model.grind;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.Validate;

/**
 * The hierarchy a job run executes on behalf of, recorded on the run so runs can be filtered
 * by owner. A job definition is a system-wide template; ownership is declared per
 * execution when the job is started and recorded on the {@link JobRun}. The platform itself is an owner too - {@link #system()} -
 * with no organization.
 * <p>
 * An owner fuses two sources: the organization and application follow from the initiating
 * participant's scope, while the owning project is declared by the caller per execution -
 * no participant carries a project.
 */
@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class JobOwner {

    private static final JobOwner SYSTEM = new JobOwner(null, null, null);

    /**
     * The owning Organization, or null for a platform (system) run.
     */
    private final String organizationId;

    /**
     * The owning Application, or null if the run is owned at the organization level.
     */
    private final String applicationId;

    /**
     * The owning Project, or null if the run is owned at the organization or application level.
     */
    private final String projectId;

    /**
     * @return true if this is the platform owner, carrying no organization
     */
    public boolean isSystem() {
        return organizationId == null;
    }

    /**
     * The platform owner, for runs the system executes on its own behalf.
     * @return the owner
     */
    public static JobOwner system() {
        return SYSTEM;
    }

    /**
     * Creates an owner from the given hierarchy ids.
     * @param organizationId the owning organization, or null for the platform owner
     * @param applicationId the owning application, or null if owned at the organization level
     * @param projectId the owning project, or null if not project-owned
     * @return the owner
     * @throws IllegalArgumentException if an application or project is given without an organization
     */
    public static JobOwner of(String organizationId, String applicationId, String projectId) {
        Validate.isTrue(organizationId != null || (applicationId == null && projectId == null),
                        "applicationId and projectId require an organizationId");
        return new JobOwner(organizationId, applicationId, projectId);
    }

    /**
     * Creates an owner at the organization level.
     * @param organizationId the owning organization
     * @return the owner
     */
    public static JobOwner ofOrganization(String organizationId) {
        return of(organizationId, null, null);
    }

    /**
     * Creates an owner at the application level.
     * @param organizationId the owning organization
     * @param applicationId the owning application
     * @return the owner
     */
    public static JobOwner ofApplication(String organizationId, String applicationId) {
        return of(organizationId, applicationId, null);
    }

}
