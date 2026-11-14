package org.kinotic.system.internal.api.services;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.kinotic.grind.api.model.JobDefinition;
import org.kinotic.grind.api.model.JobOwner;
import org.kinotic.grind.api.model.JobRun;
import org.kinotic.grind.api.model.JobRunHandle;
import org.kinotic.grind.api.repositories.JobRunRepository;
import org.kinotic.grind.api.services.JobScheduler;
import org.kinotic.grind.api.services.JobService;
import org.kinotic.system.api.model.SystemJobDescriptor;
import org.kinotic.system.api.services.SystemJobService;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultSystemJobService implements SystemJobService {

    private final List<SystemJob> systemJobs;
    private final JobService jobService;
    private final JobScheduler jobScheduler;
    private final JobRunRepository jobRunRepository;
    private final Vertx vertx;
    private final Map<String, SystemJob> jobsByName = new HashMap<>();
    private final List<SystemJobDescriptor> descriptors = new ArrayList<>();

    // A system job's name, version and description are facts of its bean, so each definition is built
    // once here to read them, and the names are checked unique before any is listed or started
    @PostConstruct
    void describeJobs() {
        for (SystemJob job : systemJobs) {
            JobDefinition definition = job.createJobDefinition();
            SystemJob other = jobsByName.put(definition.getName(), job);
            if (other != null) {
                throw new IllegalStateException("Two system jobs named " + definition.getName() + ": " + other + " and " + job);
            }
            descriptors.add(new SystemJobDescriptor(definition.getName(), definition.getVersion(), definition.getDescription()));
        }
        descriptors.sort(Comparator.comparing(SystemJobDescriptor::getName));
    }

    // Every node hosting system-api schedules the jobs, and the cluster starts one run of each a period
    @EventListener(ApplicationReadyEvent.class)
    public void scheduleJobs() {
        for (SystemJob job : systemJobs) {
            if (job.getPeriod() != null) {
                jobScheduler.schedule(job::createJobDefinition, JobOwner.system(), job.getPeriod());
            }
        }
    }

    @Override
    public Future<List<SystemJobDescriptor>> findSystemJobs() {
        return Future.succeededFuture(descriptors);
    }

    @Override
    public Future<JobRun> startSystemJob(String name) {
        Validate.notBlank(name, "name cannot be blank");
        SystemJob job = jobsByName.get(name);
        Future<JobRun> ret;
        if (job == null) {
            ret = Future.failedFuture(new IllegalArgumentException("No system job named " + name));
        } else {
            JobRunHandle handle = jobService.run(job.createJobDefinition(), JobOwner.system());
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
