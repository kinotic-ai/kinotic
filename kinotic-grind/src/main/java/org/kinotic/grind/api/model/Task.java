package org.kinotic.grind.api.model;

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
     * <p>
     * An asynchronous result belongs in the return value, or is awaited in place with the Vert.x
     * {@code Future}'s {@code await()} - both suspend the run's context so the work queued on it
     * keeps flowing. Blocking on a future with {@code CompletableFuture.get()} or {@code join()}
     * instead parks the thread while it still holds the context: a future bound to the run's
     * context, as every platform repository and service returns, delivers its completion through
     * that context and so hangs the run permanently, and a raw library future holds up the run's
     * queued record writes and event delivery until it completes.
     * @param context the job scope for the run
     * @return the result of this {@link Task}. A returned Vert.x {@code Future},
     *         {@code CompletionStage}, or {@code Publisher} is awaited and its value used.
     *         A returned {@link Task} or {@link JobDefinition} is executed as dynamically
     *         discovered tasks. Any other value is the task's result.
     */
    T execute(JobContext context) throws Exception;

}
