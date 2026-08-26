package org.kinotic.system.internal.api.services;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.core.api.exceptions.AuthorizationException;
import org.kinotic.core.api.security.Participant;
import org.kinotic.core.api.security.SecurityContext;
import org.kinotic.domain.api.model.security.ParticipantScope;
import org.kinotic.domain.api.model.security.ScopedParticipant;
import org.kinotic.management.api.model.grind.JobOwner;
import org.kinotic.management.api.model.grind.JobRun;
import org.kinotic.management.api.model.grind.Result;
import org.kinotic.management.api.model.grind.StepRecord;
import org.kinotic.management.api.services.JobRunService;
import org.kinotic.system.api.services.JobService;
import org.kinotic.management.api.services.JobMonitoringService;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Default {@link JobMonitoringService} that authorizes access through the run's recorded
 * owner - an organization or application participant may only view runs its organization
 * owns - and serves reads from {@link JobRunService} and live
 * views from {@link JobWatchService}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultJobMonitoringService implements JobMonitoringService {

    private final JobRunService jobRunService;
    private final JobService jobService;
    private final SecurityContext securityContext;

    @Override
    public Future<Page<JobRun>> findJobRuns(Pageable pageable) {
        Validate.notNull(pageable, "pageable cannot be null");
        ParticipantScope scope = currentParticipant().getScope();
        Future<Page<JobRun>> ret;
        if(scope.organizationId() == null){
            // operators troubleshoot every run, not only the platform-owned ones findAllForOwner would give
            ret = jobRunService.findAll(pageable);
        }else if(scope.applicationId() != null){
            ret = jobRunService.findAllForOwner(JobOwner.ofApplication(scope.organizationId(), scope.applicationId()),
                                                pageable);
        }else{
            ret = jobRunService.findAllForOwner(JobOwner.ofOrganization(scope.organizationId()), pageable);
        }
        return ret;
    }

    @Override
    public Future<JobRun> findJobRun(String jobRunId) {
        return authorizedJobRun(jobRunId);
    }

    @Override
    public Future<Page<StepRecord>> findSteps(String jobRunId, Pageable pageable) {
        Validate.notNull(pageable, "pageable cannot be null");
        return authorizedJobRun(jobRunId).compose(run -> jobRunService.findSteps(run.getId(), pageable));
    }

    @Override
    public Flux<Result<?>> watch(String jobRunId) {
        // Authorization starts before subscription: SecurityContext reads the calling Vert.x context
        Future<JobRun> authorized = authorizedJobRun(jobRunId);
        return Mono.fromCompletionStage(authorized.toCompletionStage())
                   .flatMapMany(run -> jobService.watchRun(run.getId()));
    }

    private Future<JobRun> authorizedJobRun(String jobRunId) {
        Validate.notBlank(jobRunId, "jobRunId cannot be blank");
        ScopedParticipant participant = currentParticipant();
        String organizationId = participant.getScope().organizationId();
        return jobRunService.findById(jobRunId).map(run -> {
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
