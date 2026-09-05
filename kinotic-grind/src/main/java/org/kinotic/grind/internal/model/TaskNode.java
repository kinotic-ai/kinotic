package org.kinotic.grind.internal.model;

import org.kinotic.grind.api.model.Store;
import org.kinotic.grind.api.model.Task;

/**
 * A task that executes one {@link Task}.
 *
 * @param sequence the node's position within its parent definition
 * @param task     the task to execute
 * @param store    how the task's result is kept
 */
public record TaskNode(int sequence,
                       Task<?> task,
                       Store store) implements JobNode {

    @Override
    public String description() {
        return task.getDescription();
    }

}
