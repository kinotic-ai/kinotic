package org.kinotic.management.internal.api.services;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.security.ParticipantScope;
import org.kinotic.grind.api.model.JobOwner;
import org.kinotic.grind.api.model.JobRun;
import org.kinotic.grind.api.model.TaskRecord;
import org.kinotic.grind.api.repositories.JobRunRepository;
import org.kinotic.grind.api.repositories.TaskRecordRepository;
import org.kinotic.management.api.services.JobMonitoringService;
import org.springframework.stereotype.Component;

/**
 * Default {@link JobMonitoringService} serving reads from the grind run repositories, with
 * access authorized through the run's recorded owner.
 */
@Component
@RequiredArgsConstructor
public class DefaultJobMonitoringService implements JobMonitoringService {

    private final JobRunRepository jobRunRepository;
    private final TaskRecordRepository taskRecordRepository;
    private final JobRunAuthorizer authorizer;

    @Override
    public Future<Page<JobRun>> findJobRuns(Pageable pageable) {
        Validate.notNull(pageable, "pageable cannot be null");
        ParticipantScope scope = authorizer.currentParticipant().getScope();
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
        return authorizer.authorizedJobRun(jobRunId);
    }

    @Override
    public Future<Page<TaskRecord>> findTasks(String jobRunId, Pageable pageable) {
        Validate.notNull(pageable, "pageable cannot be null");
        return authorizer.authorizedJobRun(jobRunId)
                         .compose(run -> taskRecordRepository.findAllForJobRun(run.getId(), pageable));
    }

}
