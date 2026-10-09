package org.kinotic.grind.api.model.events;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.kinotic.grind.api.model.TaskRecord;

/**
 * One emission from a running job's event stream: the lifecycle of each task as it happens.
 * Watchers replay every event from the start of the run, so a late subscriber sees the full
 * history before continuing live.
 *
 * Remote watchers receive each event as JSON carrying a {@code type} property naming the
 * concrete event, so the stream deserializes back into this family.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = TasksDiscoveredEvent.class, name = "tasksDiscovered"),
        @JsonSubTypes.Type(value = TaskStartedEvent.class, name = "taskStarted"),
        @JsonSubTypes.Type(value = TaskProgressEvent.class, name = "taskProgress"),
        @JsonSubTypes.Type(value = TaskCompletedEvent.class, name = "taskCompleted"),
        @JsonSubTypes.Type(value = TaskFailedEvent.class, name = "taskFailed")
})
public sealed interface JobRunEvent permits TaskStartedEvent, TaskProgressEvent, TaskCompletedEvent, TaskFailedEvent, TasksDiscoveredEvent {

    /**
     * The position of the task this event concerns, as the {@code /} separated sequence path
     * from the run's root, matching {@link TaskRecord#getTaskPath()}.
     * @return the task path
     */
    String taskPath();

}
