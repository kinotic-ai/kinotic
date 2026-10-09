package org.kinotic.grind.api.model;

import org.kinotic.grind.api.model.events.TaskProgressEvent;

/**
 * Reports a task's progress to watchers of the run as {@link TaskProgressEvent}s. Available
 * in every job scope, so a task injects it like any other dependency - an {@code @Autowired}
 * field, or a parameter of an annotated task method. Reports attach to the task executing on
 * the calling thread: a report made from a thread the task spawned is dropped.
 */
public interface ProgressReporter {

    /**
     * Reports the calling task's progress.
     * @param percentageComplete how close the task is to completion, 0 to 100
     * @param message what the task is currently doing, or null
     */
    void report(int percentageComplete, String message);

}
