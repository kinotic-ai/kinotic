package org.kinotic.grind;

import org.kinotic.grind.api.model.events.JobRunEvent;

import java.util.List;

/**
 * The observed outcome of one awaited run: every event in emission order, and the run's
 * error when it failed.
 *
 * @param events the events in emission order
 * @param error  the run's failure, or null when it completed
 */
public record RunResult(List<JobRunEvent> events, Throwable error) {
}
