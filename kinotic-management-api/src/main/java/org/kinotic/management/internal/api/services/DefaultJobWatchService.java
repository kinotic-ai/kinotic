package org.kinotic.management.internal.api.services;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import org.kinotic.core.api.Kinotic;
import org.kinotic.grind.api.model.JobRun;
import org.kinotic.grind.api.model.events.JobRunEvent;
import org.kinotic.grind.api.model.events.TaskCompletedEvent;
import org.kinotic.grind.api.services.JobService;
import org.kinotic.management.api.services.JobWatchService;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Default {@link JobWatchService} serving this node's live run streams from
 * {@link JobService#watchRun(String)}, with access authorized through the run's recorded
 * owner.
 */
@Component
@RequiredArgsConstructor
public class DefaultJobWatchService implements JobWatchService {

    private final Kinotic kinotic;
    private final JobService jobService;
    private final JobRunAuthorizer authorizer;

    @Override
    public String nodeId() {
        return kinotic.serverInfo().getNodeId();
    }

    @Override
    public Flux<JobRunEvent> watch(String jobRunId) {
        // Authorization starts before subscription: SecurityContext reads the calling Vert.x context
        Future<JobRun> authorized = authorizer.authorizedJobRun(jobRunId);
        return Mono.fromCompletionStage(authorized.toCompletionStage())
                   .flatMapMany(run -> jobService.watchRun(run.getId()))
                   .map(DefaultJobWatchService::toWireEvent);
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

}
