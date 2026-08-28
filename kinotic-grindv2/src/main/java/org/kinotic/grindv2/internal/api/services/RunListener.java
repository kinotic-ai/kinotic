package org.kinotic.grindv2.internal.api.services;

import org.kinotic.grindv2.internal.model.SerializedState;
import org.kinotic.grindv2.api.model.TaskRecord;
import org.kinotic.grindv2.api.model.StoreType;

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
     * A task finished successfully.
     * @param taskPath the task's position in the run's task tree
     * @param storeType how the task stored its result
     * @param storedName the name the result was stored under, or null
     * @param storedValue the stored value, or null
     * @param serializedState the serialized durable state, or null unless
     *                        {@code storeType} is {@link StoreType#STATE}
     */
    void taskCompleted(String taskPath,
                       StoreType storeType,
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
