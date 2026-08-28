package org.kinotic.grindv2.api.model.events;

/**
 * A task terminated with a failure.
 *
 * @param taskPath the task's position in the run's task tree
 * @param error    the failure message
 */
public record TaskFailedEvent(String taskPath, String error) implements JobRunEvent {
}
