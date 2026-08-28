package org.kinotic.grindv2.internal.api.services;

import org.kinotic.grindv2.internal.model.SerializedState;
import lombok.extern.slf4j.Slf4j;
import org.kinotic.grindv2.api.model.StoreType;
import tools.jackson.databind.ObjectMapper;

/**
 * Enforces the serialization contracts a {@code Store} can declare: a {@link StoreType#STATE}
 * value must survive a JSON round trip, so a run fails at the offending task instead of
 * persisting state that cannot be restored on resume, and a wire-published value must
 * serialize so it can reach watchers of the run.
 */
@Slf4j
public class StateSerializer {

    private final ObjectMapper objectMapper;

    public StateSerializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Serializes the given durable value.
     * @param taskDescription names the task in failure messages
     * @param value the value to serialize
     * @return the serialized state
     * @throws IllegalStateException if the value is null, generic, or not serializable
     */
    public SerializedState serialize(String taskDescription, Object value) {
        if (value == null) {
            throw new IllegalStateException("Task '" + taskDescription
                    + "' is declared Store.state but produced no value");
        }
        // Type erasure makes any generic value unrestorable: the record can only capture the
        // runtime class, not its type arguments, so replay would deserialize the contents as
        // Maps. Checking declared type parameters catches every such class in one rule.
        Class<?> valueClass = value.getClass();
        if (valueClass.getTypeParameters().length > 0) {
            throw new IllegalStateException("Task '" + taskDescription + "' is declared Store.state but produced a "
                    + valueClass.getName() + ", a generic type. Generic values such as List, Map, and Optional"
                    + " cannot be stored as STATE because Java erases their type arguments. Wrap the value in a"
                    + " domain class whose field keeps the element type, or use Store.result so the task"
                    + " reloads on resume instead");
        }
        try {
            return new SerializedState(valueClass.getName(), objectMapper.valueToTree(value));
        } catch (Exception e) {
            throw new IllegalStateException("Task '" + taskDescription + "' is declared Store.state but its value"
                    + " of type " + valueClass.getName() + " is not serializable", e);
        }
    }

    /**
     * Serializes a wire-published value. The wire form is only ever rendered, never restored,
     * so unlike {@link #serialize} it accepts generic types, and a null value returns null -
     * there is simply nothing to publish.
     * @param taskDescription names the task in failure messages
     * @param value the value to publish
     * @return the serialized value, or null when the value is null
     * @throws IllegalStateException if the value is not serializable
     */
    public SerializedState serializeWireValue(String taskDescription, Object value) {
        SerializedState ret = null;
        if (value != null) {
            try {
                ret = new SerializedState(value.getClass().getName(), objectMapper.valueToTree(value));
            } catch (Exception e) {
                throw new IllegalStateException("Task '" + taskDescription + "' is declared wire but its value"
                        + " of type " + value.getClass().getName() + " is not serializable", e);
            }
        }
        return ret;
    }

    /**
     * Restores a serialized durable value.
     * @param state the recorded state
     * @return the restored value, or null when it cannot be restored - the task then
     *         re-executes rather than replaying corrupt state
     */
    public Object deserialize(SerializedState state) {
        Object ret;
        try {
            Class<?> type = Class.forName(state.valueType());
            ret = objectMapper.treeToValue(state.value(), type);
        } catch (Exception e) {
            log.warn("Could not restore stored state of type {}, the task will re-execute", state.valueType(), e);
            ret = null;
        }
        return ret;
    }

}
