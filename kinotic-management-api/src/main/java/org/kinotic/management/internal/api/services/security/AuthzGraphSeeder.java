package org.kinotic.management.internal.api.services.security;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.grind.api.model.JobDefinition;
import org.kinotic.grind.api.model.JobOwner;
import org.kinotic.grind.api.model.JobRunHandle;
import org.kinotic.grind.api.services.JobService;
import org.kinotic.management.api.services.security.AuthzGraphSeedJob;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Runs the {@link AuthzGraphSeedJob}s of a server started with {@code kinotic.authz.seedGraph}: each once the
 * server is up, on behalf of the system, as an exclusive job. A run still executing when the server stops is
 * cancelled.
 */
@Slf4j
@Component
@ConditionalOnProperty(value = "kinotic.authz.seedGraph", havingValue = "true")
@RequiredArgsConstructor
public class AuthzGraphSeeder {

    private final List<AuthzGraphSeedJob> seedJobs;
    private final JobService jobService;
    private final List<JobRunHandle> runs = new CopyOnWriteArrayList<>();

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        for (AuthzGraphSeedJob seedJob : seedJobs) {
            JobDefinition definition = seedJob.createJobDefinition().exclusive();
            JobRunHandle run = jobService.run(definition, JobOwner.system());
            runs.add(run);
            // completion() subscribes to the run, which starts it; a run refused because another server's is
            // executing fails here like one whose task failed
            run.completion()
               .onFailure(e -> log.warn("Seed job {} did not complete as run {}: {}", definition.getName(), run.getJobRunId(), e.toString()));
        }
    }

    // A run waiting on the system model is resumed by this server's Vert.x, so one left waiting when Vert.x
    // closes would stay parked, holding every bean it reaches
    @PreDestroy
    void cancelRuns() {
        runs.forEach(JobRunHandle::cancel);
    }
}
