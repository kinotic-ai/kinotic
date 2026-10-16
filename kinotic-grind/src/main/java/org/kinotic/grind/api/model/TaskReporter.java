package org.kinotic.grind.api.model;

import org.kinotic.grind.api.model.events.TaskProgressEvent;

/**
 * Reports what a running task is doing to watchers of the run, as {@link TaskProgressEvent}s:
 * its progress, and the {@link TaskView} it shows. Available in every job scope, so a task
 * injects it like any other dependency - an {@code @Autowired} field, or a parameter of an
 * annotated task method. Reports attach to the task in flight on the calling thread's Vert.x
 * context - the run's own, where the task body executes and where the callbacks of futures bound
 * to it are dispatched until the task's result has been awaited - so a report made from a thread
 * the task spawned is dropped.
 */
public interface TaskReporter {

    /**
     * Reports the calling task's progress. Progress reaches watchers only; it is not recorded.
     * @param percentageComplete how close the task is to completion, 0 to 100
     * @param message what the task is currently doing, or null
     */
    void progress(int percentageComplete, String message);

    /**
     * Replaces the view the calling task shows. The task's record keeps the latest view.
     * @param view the view to show
     */
    void view(TaskView<?> view);

}
