package org.kinotic.grind.api.model.events;

import org.kinotic.grind.api.model.Task;
import org.kinotic.grind.api.model.TaskView;
import tools.jackson.databind.JsonNode;

/**
 * A task began executing.
 *
 * @param taskPath    the task's position in the run's task tree
 * @param description the task's description
 * @param view        the {@link TaskView} the task shows from its start, serialized as JSON, or
 *                    null when it declares none (see {@link Task#startView})
 */
public record TaskStartedEvent(String taskPath, String description, JsonNode view) implements JobRunEvent {
}
