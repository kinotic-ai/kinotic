package org.kinotic.grindv2.internal.api.services;

import org.kinotic.grindv2.internal.model.SerializedState;
import org.kinotic.grindv2.api.model.StepRecord;
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
     * Steps became known: the definition's static tree at run start, or a dynamically
     * discovered subtree while running.
     * @param parentPath the path of the step that revealed the subtree
     * @param discovered one PENDING record per revealed step, in discovery order
     * @param dynamic    true when a task produced the subtree at runtime
     */
    void stepsDiscovered(String parentPath, List<StepRecord> discovered, boolean dynamic);

    /**
     * A step began executing.
     * @param stepPath the step's position in the run's step tree
     * @param description the step's description
     */
    void stepStarted(String stepPath, String description);

    /**
     * A step finished successfully.
     * @param stepPath the step's position in the run's step tree
     * @param storeType how the step stored its result
     * @param storedName the name the result was stored under, or null
     * @param storedValue the stored value, or null
     * @param serializedState the serialized durable state, or null unless
     *                        {@code storeType} is {@link StoreType#STATE}
     */
    void stepCompleted(String stepPath,
                       StoreType storeType,
                       String storedName,
                       Object storedValue,
                       SerializedState serializedState);

    /**
     * A step terminated with a failure.
     * @param stepPath the step's position in the run's step tree
     * @param error the failure
     */
    void stepFailed(String stepPath, Throwable error);

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
