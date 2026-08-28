package org.kinotic.grind.internal.api.services;

import org.kinotic.grind.internal.model.SerializedState;
import org.kinotic.grind.api.model.TaskRecord;
import org.kinotic.grind.api.model.Store;
import org.kinotic.grind.api.model.StoreType;

import java.util.List;

/**
 * Receives the lifecycle of one job run from the {@link JobInterpreter}. The recorder
 * persists what it hears; the event stream adapter republishes it to watchers. Callbacks are
 * dispatched serially, including from parallel definitions.
 */
public interface RunListener {

    /**
     * The run began executing.
     */
    void runStarted();

    /**
     * Tasks became known: the definition's static tree at run start, or a dynamically
     * discovered subtree while running.
     * @param parentPath the path of the task that revealed the subtree
     * @param discovered one PENDING record per revealed task, in discovery order
     * @param dynamic    true when a task produced the subtree at runtime
     */
    void tasksDiscovered(String parentPath, List<TaskRecord> discovered, boolean dynamic);

    /**
     * A task began executing.
     * @param taskPath the task's position in the run's task tree
     * @param description the task's description
     */
    void taskStarted(String taskPath, String description);

    /**
     * A running task reported its progress.
     * @param taskPath the task's position in the run's task tree
     * @param percentageComplete how close the task is to completion, 0 to 100
     * @param message what the task is currently doing, or null
     */
    void taskProgress(String taskPath, int percentageComplete, String message);

    /**
     * A task finished successfully.
     * @param taskPath the task's position in the run's task tree
     * @param store how the task's result was kept
     * @param storedName the name the result was stored under, or null
     * @param storedValue the stored value, or null
     * @param serializedState the value's JSON form, or null unless the store is
     *                        {@link StoreType#STATE} or wire-published
     */
    void taskCompleted(String taskPath,
                       Store store,
                       String storedName,
                       Object storedValue,
                       SerializedState serializedState);

    /**
     * A task terminated with a failure.
     * @param taskPath the task's position in the run's task tree
     * @param error the failure
     */
    void taskFailed(String taskPath, Throwable error);

    /**
     * The run finished successfully.
     */
    void runCompleted();

    /**
     * The run terminated with a failure.
     * @param error the failure
     */
    void runFailed(Throwable error);

    /**
     * The run was cancelled before it finished.
     */
    void runCancelled();

}
