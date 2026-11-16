package org.kinotic.grind.internal.api.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.ignite.lang.IgniteCallable;
import org.apache.ignite.resources.SpringResource;
import org.kinotic.grind.api.model.ExclusiveJobRunningException;
import org.kinotic.grind.api.model.JobRunHandle;
import org.kinotic.grind.api.services.JobService;
import org.kinotic.grind.api.services.SystemJob;

/**
 * Starts a run of a {@link SystemJob} on the node the {@link JobManager} chose for it, as the compute
 * job the manager sends there. The run executes on that node, and its outcome is recorded on the run.
 */
@Slf4j
@RequiredArgsConstructor
public class SystemJobStart implements IgniteCallable<String> {

    // Injected by Ignite on the node that executes the compute job
    @SpringResource(resourceClass = JobService.class)
    private transient JobService jobService;

    // the name of the system job to start
    private final String name;

    /**
     * @return the id of the run started
     * @throws IllegalArgumentException when this node has no system job of the name
     */
    @Override
    public String call() {
        JobRunHandle handle = jobService.runSystemJob(name);
        String jobRunId = handle.getJobRunId();
        // the subscription is what starts the run
        handle.completion().onFailure(error -> {
            if (error instanceof ExclusiveJobRunningException) {
                log.warn("Scheduled system job {} came due while a run of it was still executing, so run {} was refused",
                         name, jobRunId, error);
            } else {
                log.warn("Scheduled system job {} run {} failed", name, jobRunId, error);
            }
        });
        return jobRunId;
    }

}
