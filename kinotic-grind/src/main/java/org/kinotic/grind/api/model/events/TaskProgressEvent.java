package org.kinotic.grind.api.model.events;

import org.kinotic.grind.api.model.TaskReporter;
import org.kinotic.grind.api.model.TaskView;
import tools.jackson.databind.JsonNode;

/**
 * A running task reported through the {@link TaskReporter}: its progress, or a new view. Emitted
 * between the task's {@link TaskStartedEvent} and its terminal event, as often as the task
 * reports.
 *
 * @param taskPath           the task's position in the run's task tree
 * @param percentageComplete how close the task is to completion, 0 to 100, or null for a report
 *                           that only replaces the view
 * @param message            what the task is currently doing, or null
 * @param view               the {@link TaskView} the task now shows, serialized as JSON, or null
 *                           when the report leaves it unchanged
 */
public record TaskProgressEvent(String taskPath,
                                Integer percentageComplete,
                                String message,
                                JsonNode view) implements JobRunEvent {
}
