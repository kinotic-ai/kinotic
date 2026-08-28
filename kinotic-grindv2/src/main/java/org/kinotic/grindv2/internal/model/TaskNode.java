package org.kinotic.grindv2.internal.model;

import org.kinotic.grindv2.api.model.StoreType;
import org.kinotic.grindv2.api.model.Task;

/**
 * A task that executes one {@link Task}.
 *
 * @param sequence   the node's position within its parent definition
 * @param task       the task to execute
 * @param reloadTask executed instead of {@code task} when a resumed run finds this task
 *                   already completed, or null if the task is its own reload
 * @param storeType  how the task's result is kept
 * @param resultName the name to store the result under, or null to derive one from the value
 */
public record TaskNode(int sequence,
                       Task<?> task,
                       Task<?> reloadTask,
                       StoreType storeType,
                       String resultName) implements JobNode {

    @Override
    public String description() {
        return task.getDescription();
    }

}
