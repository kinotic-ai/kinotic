package org.kinotic.grindv2.api.model;

/**
 * A step terminated with a failure.
 *
 * @param stepPath the step's position in the run's step tree
 * @param error    the failure message
 */
public record StepFailed(String stepPath, String error) implements JobRunEvent {
}
