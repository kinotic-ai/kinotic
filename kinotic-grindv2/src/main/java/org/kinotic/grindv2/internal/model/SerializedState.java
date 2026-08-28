package org.kinotic.grindv2.internal.model;

import tools.jackson.databind.JsonNode;

/**
 * A task's durable state value serialized for its {@link org.kinotic.grindv2.api.model.TaskRecord}.
 *
 * @param valueType the Java type of the stored value, used to deserialize on resume
 * @param value     the value serialized as JSON
 */
public record SerializedState(String valueType, JsonNode value) {
}
