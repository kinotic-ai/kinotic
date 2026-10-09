package org.kinotic.grind.api.model.events;

import org.kinotic.grind.api.model.TaskLogEntry;
import org.kinotic.grind.api.model.TaskLogger;

/**
 * A running task wrote a line through its {@link TaskLogger}. Emitted between the task's
 * {@link TaskStartedEvent} and its terminal event, once per line.
 *
 * @param taskPath the task's position in the run's task tree
 * @param entry    the line the task wrote
 */
public record TaskLogEvent(String taskPath,
                           TaskLogEntry entry) implements JobRunEvent {
}
