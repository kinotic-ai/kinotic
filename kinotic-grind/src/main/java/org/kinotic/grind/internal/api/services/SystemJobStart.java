package org.kinotic.grind.internal.api.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.ignite.lang.IgniteCallable;
import org.apache.ignite.resources.SpringResource;
import org.kinotic.grind.api.model.ExclusiveJobRunningException;
import org.kinotic.grind.api.model.JobRunHandle;
import org.kinotic.grind.api.services.JobService;

import java.time.Duration;

/**
 * Starts a run of a {@link JobService#findSystemJobs() system job} on the node the {@link JobManager} chose for it, as the compute
 * job the manager sends there, and answers once the run is on record. The run executes on that node,
 * and its outcome is recorded on the run.
 */
@Slf4j
@RequiredArgsConstructor
public class SystemJobStart implements IgniteCallable<String> {

    // How long the run is given to be on record, well inside the time the manager waits for the answer
    private static final Duration RECORD_TIMEOUT = Duration.ofSeconds(20);

    // Injected by Ignite on the node that executes the compute job
    @SpringResource(resourceClass = JobService.class)
    private transient JobService jobService;

    // the name of the system job to start
    private final String name;

    /**
     * @return the id of the run started, or null when the run was refused because another run of the
     *         job is executing
     * @throws IllegalArgumentException when this node has no system job of the name
     * @throws IllegalStateException when the run could not be recorded, and so did not start
     */
    @Override
    public String call() {
        JobRunHandle handle = jobService.runSystemJob(name);
        String jobRunId = handle.getJobRunId();
        // the subscription is what starts the run
        handle.completion().onFailure(error -> {
            if (!(error instanceof ExclusiveJobRunningException)) {
                log.warn("Scheduled system job {} run {} failed", name, jobRunId, error);
            }
        });
        String ret;
        try {
            // The run and its PENDING task records are written before its first event is emitted
            handle.getEvents().blockFirst(RECORD_TIMEOUT);
            ret = jobRunId;
        } catch (ExclusiveJobRunningException e) {
            ret = null;
        }
        return ret;
    }

}
