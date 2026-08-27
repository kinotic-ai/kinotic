package org.kinotic.grindv2.api;

import java.util.List;

/**
 * A step produced dynamically discovered steps at runtime, carrying the discovered subtree as
 * PENDING {@link StepRecord}s in discovery order.
 *
 * @param stepPath the position of the step that produced the discovery
 * @param steps    the discovered steps, recorded PENDING
 */
public record StepsDiscovered(String stepPath, List<StepRecord> steps) implements JobRunEvent {
}
