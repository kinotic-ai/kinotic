package org.kinotic.grindv2.api.model;

/**
 * A unit of work executed as one task of a {@link JobDefinition}.
 */
public interface Task<T> {

    /**
     * @return the description of this {@link Task} shown in run records
     */
    String getDescription();

    /**
     * Performs the work of this {@link Task}.
     * @param context the job scope for the run
     * @return the result of this {@link Task}. A returned {@code Future},
     *         {@code CompletionStage}, or {@code Publisher} is awaited and its value used.
     *         A returned {@link Task} or {@link JobDefinition} is executed as dynamically
     *         discovered tasks. Any other value is the task's result.
     */
    T execute(JobContext context) throws Exception;

}
