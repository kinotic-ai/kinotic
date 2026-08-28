package org.kinotic.management.internal.api.services;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.exceptions.AuthorizationException;
import org.kinotic.core.api.security.Participant;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.domain.api.model.security.ScopedParticipant;
import org.kinotic.grind.api.model.JobRun;
import org.kinotic.grind.api.repositories.JobRunRepository;
import org.springframework.stereotype.Component;

/**
 * Authorizes the calling participant's access to grind job runs through the run's recorded
 * owner: an organization or application participant may only view runs its organization owns,
 * a system participant may view any run.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobRunAuthorizer {

    private final JobRunRepository jobRunRepository;
    private final SecurityContext securityContext;

    /**
     * Loads the given run and verifies the calling participant may view it.
     * @param jobRunId the id of the run
     * @return a future that will complete with the run, or fail if the run does not exist or
     *         belongs to another organization
     */
    public Future<JobRun> authorizedJobRun(String jobRunId) {
        Validate.notBlank(jobRunId, "jobRunId cannot be blank");
        ScopedParticipant participant = currentParticipant();
        String organizationId = participant.getScope().organizationId();
        return jobRunRepository.findRun(jobRunId).map(run -> {
            if(run == null){
                throw new IllegalArgumentException("JobRun not found: " + jobRunId);
            }
            // a null organizationId is a SYSTEM-scoped caller, who may view any run
            if(organizationId != null && !organizationId.equals(run.getOrganizationId())){
                // Log the mismatch server-side; surface only a generic message to the caller
                log.error("Participant {} may not view job run {} (run org={})",
                          participant.getId(), jobRunId, run.getOrganizationId());
                throw new AuthorizationException("Access denied");
            }
            return run;
        });
    }

    /**
     * The calling participant, which must be bound to the current Vert.x context and carry a
     * hierarchy scope.
     * @return the participant
     */
    public ScopedParticipant currentParticipant() {
        Participant participant = securityContext.currentParticipant();
        if(participant == null){
            throw new IllegalStateException("No Participant is bound to the current Vert.x context");
        }
        if(!(participant instanceof ScopedParticipant scoped)){
            // only a hierarchy-scoped participant can be matched against a run's owner
            throw new AuthorizationException("Access denied");
        }
        return scoped;
    }

}
