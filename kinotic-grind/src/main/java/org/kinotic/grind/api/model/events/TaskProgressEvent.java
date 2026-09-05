package org.kinotic.grind.api.model.events;

import org.kinotic.grind.api.model.ProgressReporter;

/**
 * A running task reported its progress through the {@link ProgressReporter}. Emitted between
 * the task's {@link TaskStartedEvent} and its terminal event, as often as the task reports.
 *
 * @param taskPath           the task's position in the run's task tree
 * @param percentageComplete how close the task is to completion, 0 to 100
 * @param message            what the task is currently doing, or null
 */
public record TaskProgressEvent(String taskPath,
                                int percentageComplete,
                                String message) implements JobRunEvent {
}
