package org.kinotic.grindv2.api.model;

/**
 * One emission from a running job's event stream: the lifecycle of each task as it happens.
 * Watchers replay every event from the start of the run, so a late subscriber sees the full
 * history before continuing live.
 */
public sealed interface JobRunEvent permits TaskStarted, TaskCompleted, TaskFailed, TasksDiscovered {

    /**
     * The position of the task this event concerns, as the {@code /} separated sequence path
     * from the run's root, matching {@link TaskRecord#getTaskPath()}.
     * @return the task path
     */
    String taskPath();

}
