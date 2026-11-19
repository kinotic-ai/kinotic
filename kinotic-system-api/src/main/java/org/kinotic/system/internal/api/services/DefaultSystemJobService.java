package org.kinotic.system.internal.api.services;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.ignite.Ignite;
import org.kinotic.core.api.event.ZonePartitioningService;
import org.kinotic.domain.api.utils.DomainUtil;
import org.kinotic.grind.api.model.JobRun;
import org.kinotic.grind.api.repositories.JobRunRepository;
import org.kinotic.grind.api.services.JobService;
import org.kinotic.system.api.model.SystemJobDescriptor;
import org.kinotic.system.api.services.SystemJobService;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultSystemJobService implements SystemJobService {

    private final JobService jobService;
    private final JobRunRepository jobRunRepository;
    private final Vertx vertx;
    private final Ignite ignite;

    // Every node requests the deployment; Ignite elects a single host for the manager among the nodes
    // hosting system-api, the only ones whose context holds the system jobs
    @EventListener(ApplicationReadyEvent.class)
    public void deployJobManager() {
        jobService.deployJobManager(ignite.cluster().forAttribute(ZonePartitioningService.hostsAttribute(DomainUtil.SYSTEM_API_ZONE), true));
    }

    @Override
    public Future<List<SystemJobDescriptor>> findSystemJobs() {
        return Future.succeededFuture(jobService.findSystemJobs().stream()
                                                .map(job -> new SystemJobDescriptor(job.getName(), job.getVersion(), job.getDescription()))
                                                .toList());
    }

    @Override
    public Future<JobRun> startSystemJob(String name) {
        // mapped from a succeeded future, so a name the job service refuses fails the returned future
        return Future.succeededFuture()
                     .map(v -> jobService.runSystemJob(name))
                     .compose(handle -> {
                         String jobRunId = handle.getJobRunId();
                         log.info("Starting system job {} as run {}", name, jobRunId);
                         // The outcome is recorded on the run; the subscription is what starts it
                         handle.completion().onFailure(error -> log.warn("System job {} run {} failed", name, jobRunId, error));
                         // The run and its PENDING task records are written before its first event is emitted, so
                         // the run can be read back once that event arrives
                         return Future.fromCompletionStage(handle.getEvents().next().toFuture(), vertx.getOrCreateContext())
                                      .compose(firstEvent -> jobRunRepository.findRun(jobRunId));
                     });
    }

}
