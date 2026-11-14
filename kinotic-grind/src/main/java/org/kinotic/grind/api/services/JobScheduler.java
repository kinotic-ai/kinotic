package org.kinotic.grind.api.services;

import org.kinotic.grind.api.model.JobDefinition;
import org.kinotic.grind.api.model.JobOwner;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Runs jobs on a schedule. A scheduled run is a run as {@link JobService#run(JobDefinition, JobOwner)}
 * starts one, so it is recorded with its tasks and listed with the job's other runs.
 */
public interface JobScheduler {

    /**
     * Runs the job for the owner once every period, counted across the cluster from the start of its
     * last scheduled run: the first run starts when the cluster first schedules the job. Every node
     * able to run the job schedules it, and each run executes on one of them, so a node joining or
     * leaving the cluster starts no run of its own. A run due while another run of the job executes,
     * scheduled or not, starts once that run has ended.
     *
     * @param definitions builds the definition of each run, always exclusive and of the same name
     * @param owner the hierarchy the runs execute on behalf of, {@link JobOwner#system()} for system runs
     * @param period the time from the start of one scheduled run to the start of the next
     */
    void schedule(Supplier<JobDefinition> definitions, JobOwner owner, Duration period);

}
