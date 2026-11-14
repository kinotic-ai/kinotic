package org.kinotic.system.internal.api.services;

import org.kinotic.grind.api.model.JobDefinition;
import org.kinotic.system.api.services.SystemJobService;

import java.time.Duration;

/**
 * A grind job system users can start on demand through {@link SystemJobService}, which also runs it
 * every {@link #getPeriod()} when it has one. Every Spring bean implementing this interface is
 * listed, and each start runs a fresh definition on behalf of the system. The console shows each task with the icon and the views it declares; a
 * step that runs a workload shows its log by declaring it as the step's start view.
 */
public interface SystemJob {

    /**
     * Builds the definition of one run of the job.
     *
     * @return a fresh definition, its name and version set; the name identifies the job and must be
     *         unique among the system jobs
     */
    JobDefinition createJobDefinition();

    /**
     * How often the job also runs by itself, one run across the cluster each period. A job that runs
     * by itself builds exclusive definitions.
     *
     * @return the time from the start of one such run to the start of the next, or null for a job
     *         that runs only when it is started
     */
    default Duration getPeriod() {
        return null;
    }

}
