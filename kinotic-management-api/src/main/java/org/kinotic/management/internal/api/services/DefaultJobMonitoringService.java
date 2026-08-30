package org.kinotic.management.internal.api.services;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.Kinotic;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.exceptions.AuthorizationException;
import org.kinotic.core.api.security.Participant;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.domain.api.model.security.participant.ParticipantScope;
import org.kinotic.domain.api.model.security.participant.ScopedParticipant;
import org.kinotic.grind.api.model.JobOwner;
import org.kinotic.grind.api.model.JobRun;
import org.kinotic.grind.api.model.TaskRecord;
import org.kinotic.grind.api.model.events.JobRunEvent;
import org.kinotic.grind.api.model.events.TaskCompletedEvent;
import org.kinotic.grind.api.repositories.JobRunRepository;
import org.kinotic.grind.api.repositories.TaskRecordRepository;
import org.kinotic.grind.api.services.JobService;
import org.kinotic.management.api.services.JobMonitoringService;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Default {@link JobMonitoringService} serving reads from the grind run repositories and live
 * views from {@link JobService#watchRun(String)}, with access authorized through the run's
 * recorded owner.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultJobMonitoringService implements JobMonitoringService {

    private final Kinotic kinotic;
    private final JobRunRepository jobRunRepository;
    private final TaskRecordRepository taskRecordRepository;
    private final JobService jobService;
    private final SecurityContext securityContext;

    @Override
    public String nodeId() {
        return kinotic.serverInfo().getNodeId();
    }

    @Override
    public Future<Page<JobRun>> findJobRuns(Pageable pageable) {
        Validate.notNull(pageable, "pageable cannot be null");
        ParticipantScope scope = currentParticipant().getScope();
        Future<Page<JobRun>> ret;
        if(scope.organizationId() == null){
            // operators troubleshoot every run, not only the platform-owned ones findAllForOwner would give
            ret = jobRunRepository.findAll(pageable);
        }else if(scope.applicationId() != null){
            ret = jobRunRepository.findAllForOwner(JobOwner.ofApplication(scope.organizationId(), scope.applicationId()),
                                                   pageable);
        }else{
            ret = jobRunRepository.findAllForOwner(JobOwner.ofOrganization(scope.organizationId(), null), pageable);
        }
        return ret;
    }

    @Override
    public Future<JobRun> findJobRun(String jobRunId) {
        return authorizedJobRun(jobRunId);
    }

    @Override
    public Future<Page<TaskRecord>> findTasks(String jobRunId, Pageable pageable) {
        Validate.notNull(pageable, "pageable cannot be null");
        return authorizedJobRun(jobRunId).compose(run -> taskRecordRepository.findAllForJobRun(run.getId(), pageable));
    }

    @Override
    public Flux<JobRunEvent> watch(String jobRunId) {
        // Authorization starts before subscription: SecurityContext reads the calling Vert.x context
        Future<JobRun> authorized = authorizedJobRun(jobRunId);
        return Mono.fromCompletionStage(authorized.toCompletionStage())
                   .flatMapMany(run -> jobService.watchRun(run.getId()))
                   .map(DefaultJobMonitoringService::toWireEvent);
    }

    /**
     * Rebuilds a {@link TaskCompletedEvent} without the live {@code storedValue} it carries
     * for in-process subscribers: an arbitrary user object that cannot cross a serialization
     * boundary. The {@code wireValue} the task's store published is already JSON, so it
     * stays. Every other event passes through untouched.
     */
    private static JobRunEvent toWireEvent(JobRunEvent event) {
        JobRunEvent ret;
        if(event instanceof TaskCompletedEvent completed){
            ret = new TaskCompletedEvent(completed.taskPath(),
                                         completed.storeType(),
                                         completed.storedName(),
                                         null,
                                         completed.wireValue());
        }else{
            ret = event;
        }
        return ret;
    }

    private Future<JobRun> authorizedJobRun(String jobRunId) {
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

    private ScopedParticipant currentParticipant() {
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
