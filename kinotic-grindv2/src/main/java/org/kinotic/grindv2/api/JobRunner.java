package org.kinotic.grindv2.api;

/**
 * Executes {@link JobDefinition}s as recorded runs: a {@link JobRun} is persisted for the run
 * and a {@link StepRecord} for every step, through the configured {@link JobRunRepository}.
 */
public interface JobRunner {

    /**
     * Prepares a recorded execution of the given {@link JobDefinition} on behalf of the given
     * {@link JobOwner}. The run starts, and its records are written, when the returned
     * handle's events are first subscribed - {@link JobRunHandle#completion()} subscribes.
     * @param jobDefinition to execute, its {@link JobDefinition#getName()} must be set
     * @param owner the hierarchy this run executes on behalf of, {@link JobOwner#system()}
     *              for a platform run
     * @return the prepared {@link JobRunHandle}
     */
    JobRunHandle run(JobDefinition jobDefinition, JobOwner owner);

    /**
     * Prepares a recorded execution that resumes a previous run: steps the original run
     * completed are not executed again, according to each step's {@link StoreType}. The
     * resume is recorded as a new {@link JobRun} referencing the original, owned by the
     * original run's owner.
     *
     * The given {@link JobDefinition} must be freshly built by the same code that built the
     * original run's definition: its name and version must match the recorded run, and its
     * step structure must be unchanged, or replayed steps will not line up with their records.
     * @param jobRunId the id of the FAILED or CANCELLED run to resume
     * @param jobDefinition the freshly built definition to execute
     * @return the prepared {@link JobRunHandle}
     */
    JobRunHandle resume(String jobRunId, JobDefinition jobDefinition);

}
