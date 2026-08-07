package org.kinotic.orchestrator.api.grind;

import lombok.Getter;
import org.apache.commons.lang3.Validate;

/**
 * The hierarchy a job run executes on behalf of, recorded on the run so runs can be filtered
 * by owner. A {@link JobDefinition} is a system-wide template; ownership is declared per
 * execution when the job is started.
 */
@Getter
public class JobOwner {

    /**
     * The owning Organization.
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

    public JobOwner(String organizationId, String applicationId, String projectId) {
        Validate.notBlank(organizationId, "organizationId cannot be blank");
        this.organizationId = organizationId;
        this.applicationId = applicationId;
        this.projectId = projectId;
    }

    /**
     * Creates an owner at the organization level.
     * @param organizationId the owning organization
     * @return the owner
     */
    public static JobOwner ofOrganization(String organizationId) {
        return new JobOwner(organizationId, null, null);
    }

    /**
     * Creates an owner at the application level.
     * @param organizationId the owning organization
     * @param applicationId the owning application
     * @return the owner
     */
    public static JobOwner ofApplication(String organizationId, String applicationId) {
        return new JobOwner(organizationId, applicationId, null);
    }

}
