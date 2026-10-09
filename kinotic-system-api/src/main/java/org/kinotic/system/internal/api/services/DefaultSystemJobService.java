package org.kinotic.system.internal.api.services;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.kinotic.grind.api.model.JobDefinition;
import org.kinotic.grind.api.model.JobOwner;
import org.kinotic.grind.api.model.JobRun;
import org.kinotic.grind.api.model.JobRunHandle;
import org.kinotic.grind.api.repositories.JobRunRepository;
import org.kinotic.grind.api.services.JobService;
import org.kinotic.system.api.model.SystemJobDescriptor;
import org.kinotic.system.api.services.SystemJob;
import org.kinotic.system.api.services.SystemJobService;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultSystemJobService implements SystemJobService {

    private final List<SystemJob> systemJobs;
    private final JobService jobService;
    private final JobRunRepository jobRunRepository;
    private final Vertx vertx;

    @Override
    public Future<List<SystemJobDescriptor>> findSystemJobs() {
        List<SystemJobDescriptor> descriptors =
                systemJobs.stream()
                          .map(job -> {
                              JobDefinition definition = job.createJobDefinition();
                              return new SystemJobDescriptor(definition.getName(),
                                                             definition.getVersion(),
                                                             definition.getDescription());
                          })
                          .sorted(Comparator.comparing(SystemJobDescriptor::getName))
                          .toList();
        return Future.succeededFuture(descriptors);
    }

    @Override
    public Future<JobRun> startSystemJob(String name) {
        Validate.notBlank(name, "name cannot be blank");
        JobDefinition definition = systemJobs.stream()
                                             .map(SystemJob::createJobDefinition)
                                             .filter(candidate -> name.equals(candidate.getName()))
                                             .findFirst()
                                             .orElse(null);
        Future<JobRun> ret;
        if (definition == null) {
            ret = Future.failedFuture(new IllegalArgumentException("No system job named " + name));
        } else {
            JobRunHandle handle = jobService.run(definition, JobOwner.system());
            String jobRunId = handle.getJobRunId();
            log.info("Starting system job {} as run {}", name, jobRunId);
            // The outcome is recorded on the run; the subscription is what starts it
            handle.completion().onFailure(error -> log.warn("System job {} run {} failed", name, jobRunId, error));
            // The run and its PENDING task records are written before its first event is emitted, so
            // the run can be read back once that event arrives
            ret = Future.fromCompletionStage(handle.getEvents().next().toFuture(), vertx.getOrCreateContext())
                        .compose(firstEvent -> jobRunRepository.findRun(jobRunId));
        }
        return ret;
    }

}
