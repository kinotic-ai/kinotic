package org.kinotic.grindv2.api.model;

/**
 * A step began executing.
 *
 * @param stepPath    the step's position in the run's step tree
 * @param description the step's description
 */
public record StepStarted(String stepPath, String description) implements JobRunEvent {
}
