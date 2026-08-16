package org.kinotic.os.internal.api.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.exceptions.AuthorizationException;
import org.kinotic.core.api.security.Participant;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.domain.api.model.security.ApplicationParticipant;
import org.kinotic.domain.api.model.security.OrganizationParticipant;
import org.kinotic.domain.api.model.security.ScopedParticipant;
import org.kinotic.domain.api.model.security.SystemParticipant;
import org.kinotic.orchestrator.api.model.grind.JobOwner;
import org.kinotic.orchestrator.api.model.grind.JobRun;
import org.kinotic.orchestrator.api.model.grind.Result;
import org.kinotic.orchestrator.api.model.grind.TaskRecord;
import org.kinotic.orchestrator.api.services.JobRunService;
import org.kinotic.orchestrator.api.services.JobService;
import org.kinotic.orchestrator.api.services.TaskRecordService;
import org.kinotic.os.api.services.JobMonitoringService;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.concurrent.CompletableFuture;

/**
 * Default {@link JobMonitoringService} that authorizes access through the run's recorded
 * owner - an organization or application participant may only view runs its organization
 * owns - and serves reads from {@link JobRunService} and {@link TaskRecordService} and live
 * views from {@link JobService}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultJobMonitoringService implements JobMonitoringService {

    private final JobRunService jobRunService;
    private final TaskRecordService taskRecordService;
    private final JobService jobService;
    private final SecurityContext securityContext;

    @Override
    public CompletableFuture<Page<JobRun>> findJobRuns(Pageable pageable) {
        Validate.notNull(pageable, "pageable cannot be null");
        ScopedParticipant participant = currentParticipant();
        CompletableFuture<Page<JobRun>> ret;
        if(participant instanceof SystemParticipant){
            // operators troubleshoot every run, not only the platform-owned ones findAllForOwner would give
            ret = jobRunService.findAll(pageable);
        }else{
            ret = jobRunService.findAllForOwner(JobOwner.from(participant), pageable);
        }
        return ret;
    }

    @Override
    public CompletableFuture<JobRun> findJobRun(String jobRunId) {
        return authorizedJobRun(jobRunId);
    }

    @Override
    public CompletableFuture<Page<TaskRecord>> findTaskRecords(String jobRunId, Pageable pageable) {
        Validate.notNull(pageable, "pageable cannot be null");
        return authorizedJobRun(jobRunId).thenCompose(run -> taskRecordService.findAllForJobRun(run.getId(), pageable));
    }

    @Override
    public Flux<Result<?>> watch(String jobRunId) {
        // Authorization starts before subscription: SecurityContext reads the calling Vert.x context
        CompletableFuture<JobRun> authorized = authorizedJobRun(jobRunId);
        return Mono.fromFuture(authorized)
                   .flatMapMany(run -> jobService.watchExecution(run.getId()));
    }

    private CompletableFuture<JobRun> authorizedJobRun(String jobRunId) {
        Validate.notBlank(jobRunId, "jobRunId cannot be blank");
        ScopedParticipant participant = currentParticipant();
        return jobRunService.findById(jobRunId).thenApply(run -> {
            if(run == null){
                throw new IllegalArgumentException("JobRun not found: " + jobRunId);
            }
            boolean mayView = switch(participant){
                case SystemParticipant ignored -> true;
                case OrganizationParticipant org -> org.getOrganizationId().equals(run.getOrganizationId());
                case ApplicationParticipant app -> app.getOrganizationId().equals(run.getOrganizationId());
            };
            if(!mayView){
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
