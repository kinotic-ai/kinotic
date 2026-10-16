package org.kinotic.grind.internal.model;

import org.kinotic.grind.internal.api.model.DefaultTask;
import org.kinotic.grind.api.model.Store;
import org.kinotic.grind.api.model.Task;

/**
 * A task that executes one {@link Task}.
 *
 * @param sequence the node's position within its parent definition
 * @param task     the task to execute
 * @param store    how the task's result is kept: the task's own {@link Store}, or the one it
 *                 inherits when it declares none
 */
public record TaskNode(int sequence,
                       Task<?> task,
                       Store store) implements JobNode {

    @Override
    public String description() {
        return task.getDescription();
    }

    @Override
    public String icon() {
        return task.getIcon();
    }

    /**
     * @return the view the task shows from its start, or null when it declares none
     */
    public ViewProducer startView() {
        return task instanceof DefaultTask<?> declared ? declared.getStartView() : null;
    }

    /**
     * @return the view the task shows once it completes, or null when it declares none
     */
    public ViewProducer resultView() {
        return task instanceof DefaultTask<?> declared ? declared.getResultView() : null;
    }

}
