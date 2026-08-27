package org.kinotic.system.api.model.grind;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.Validate;

/**
 * The hierarchy tier a job run executes on behalf of, recorded on the run so runs can be
 * filtered by owner. A job definition is a system-wide template; ownership is declared per
 * execution when the job is started and recorded on the {@link JobRun}. Runs are owned at one
 * of the platform's three tiers: the platform itself ({@link #system()}, no organization and
 * no application), an organization ({@link #ofOrganization}), or an application
 * ({@link #ofApplication}). An organization or application owner may additionally name the
 * project the run served - the project is declared by the caller per execution, it never
 * follows from a participant.
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
     * The owning Application, or null if the run is owned at the system or organization tier.
     */
    private final String applicationId;

    /**
     * The Project the run served, or null if the run was not for a project.
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
     * Creates an owner at the organization tier.
     * @param organizationId the owning organization
     * @return the owner
     */
    public static JobOwner ofOrganization(String organizationId) {
        return ofOrganization(organizationId, null);
    }

    /**
     * Creates an owner at the organization tier, naming the project the run serves.
     * @param organizationId the owning organization
     * @param projectId the project the run serves, or null if the run is not for a project
     * @return the owner
     */
    public static JobOwner ofOrganization(String organizationId, String projectId) {
        Validate.notBlank(organizationId, "organizationId cannot be blank");
        return new JobOwner(organizationId, null, projectId);
    }

    /**
     * Creates an owner at the application tier.
     * @param organizationId the owning organization
     * @param applicationId the owning application
     * @return the owner
     */
    public static JobOwner ofApplication(String organizationId, String applicationId) {
        return ofApplication(organizationId, applicationId, null);
    }

    /**
     * Creates an owner at the application tier, naming the project the run serves.
     * @param organizationId the owning organization
     * @param applicationId the owning application
     * @param projectId the project the run serves, or null if the run is not for a project
     * @return the owner
     */
    public static JobOwner ofApplication(String organizationId, String applicationId, String projectId) {
        Validate.notBlank(organizationId, "organizationId cannot be blank");
        Validate.notBlank(applicationId, "applicationId cannot be blank");
        return new JobOwner(organizationId, applicationId, projectId);
    }

}
