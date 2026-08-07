package org.kinotic.orchestrator.api.grind;

import lombok.Getter;
import org.apache.commons.lang3.Validate;
import org.kinotic.domain.api.security.ApplicationParticipant;
import org.kinotic.domain.api.security.OrganizationParticipant;
import org.kinotic.domain.api.security.ScopedParticipant;
import org.kinotic.domain.api.security.SystemParticipant;

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

    /**
     * Creates the owner matching the given participant's scope, so a run started on a
     * participant's behalf is owned by the hierarchy the participant is authenticated under.
     * @param participant whose scope owns the run
     * @return the owner, or null for a {@link SystemParticipant} - the platform owner
     */
    public static JobOwner from(ScopedParticipant participant) {
        Validate.notNull(participant, "participant cannot be null");
        return switch (participant) {
            case SystemParticipant ignored -> null;
            case OrganizationParticipant org -> ofOrganization(org.getOrganizationId());
            case ApplicationParticipant app -> ofApplication(app.getOrganizationId(), app.getApplicationId());
        };
    }

    /**
     * Creates the owner matching the given participant's scope, narrowed to the given project.
     * @param participant whose scope owns the run
     * @param projectId the owning project, or null for no project narrowing
     * @return the owner, or null for a {@link SystemParticipant} with no project
     * @throws NullPointerException if a project is given for a {@link SystemParticipant},
     *         since a project-owned run requires an owning organization
     */
    public static JobOwner from(ScopedParticipant participant, String projectId) {
        JobOwner ret = from(participant);
        if(projectId != null){
            Validate.notNull(ret, "a SystemParticipant cannot own a project-scoped run");
            ret = new JobOwner(ret.organizationId, ret.applicationId, projectId);
        }
        return ret;
    }

}
