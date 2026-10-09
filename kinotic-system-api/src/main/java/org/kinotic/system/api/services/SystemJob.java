package org.kinotic.system.api.services;

import org.kinotic.grind.api.model.JobDefinition;

/**
 * A grind job the platform's operators can start on demand through {@link SystemJobService}. Every
 * Spring bean implementing this interface is listed, and each start runs a fresh definition on
 * behalf of the platform.
 *
 * A step that runs a platform workload is a nested {@link JobDefinition} whose task stores the
 * workload's id under {@link #WORKLOAD_ID} as wired state, before the task that runs the workload:
 * the console then shows that workload's console log on the step's row, live while it runs and from
 * the run's history afterwards.
 *
 * <pre>{@code
 * JobDefinition.create("Run the check")
 *              .task(Tasks.fromValue("Allocate workload", UUID.randomUUID().toString()),
 *                    Store.state(SystemJob.WORKLOAD_ID).wire())
 *              .task(Tasks.fromCallable("Run the check's VM", runWorkload));
 * }</pre>
 */
public interface SystemJob {

    /**
     * The name a step's task stores the id of the workload the step runs under.
     */
    String WORKLOAD_ID = "workloadId";

    /**
     * Builds the definition of one run of the job.
     *
     * @return a fresh definition, its name and version set; the name identifies the job and must be
     *         unique among the platform's system jobs
     */
    JobDefinition createJobDefinition();

}
