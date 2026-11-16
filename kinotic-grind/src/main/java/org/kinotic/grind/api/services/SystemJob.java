package org.kinotic.grind.api.services;

import org.kinotic.grind.api.model.JobDefinition;

/**
 * A job of the system's own. Every Spring bean implementing this interface is one of the node's
 * system jobs: {@link JobService#findSystemJobs()} lists it, and {@link JobService#runSystemJob(String)}
 * runs a fresh definition of it on behalf of the system. A job whose definition declares a period
 * with {@link JobDefinition#every(java.time.Duration)} is started by the cluster's job manager each
 * time it is due. The console shows each task with the icon and the views it declares; a step that
 * runs a workload shows its log by declaring it as the step's start view.
 */
public interface SystemJob {

    /**
     * Builds the definition of one run of the job.
     *
     * @return a fresh definition, its name and version set; the name identifies the job and must be
     *         unique among the system jobs
     */
    JobDefinition createJobDefinition();

}
