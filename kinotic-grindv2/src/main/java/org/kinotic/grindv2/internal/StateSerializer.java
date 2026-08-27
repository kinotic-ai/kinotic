package org.kinotic.grindv2.internal;

import lombok.extern.slf4j.Slf4j;
import org.kinotic.grindv2.api.StoreType;
import tools.jackson.databind.ObjectMapper;

/**
 * Enforces the {@link StoreType#STATE} contract: a durable value must survive a JSON round
 * trip, so a run fails at the offending step instead of persisting state that cannot be
 * restored on resume.
 */
@Slf4j
public class StateSerializer {

    private final ObjectMapper objectMapper;

    public StateSerializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Serializes the given durable value.
     * @param stepDescription names the step in failure messages
     * @param value the value to serialize
     * @return the serialized state
     * @throws IllegalStateException if the value is null, generic, or not serializable
     */
    public SerializedState serialize(String stepDescription, Object value) {
        if (value == null) {
            throw new IllegalStateException("Step '" + stepDescription
                    + "' is declared taskStoreState but produced no value");
        }
        // Type erasure makes any generic value unrestorable: the record can only capture the
        // runtime class, not its type arguments, so replay would deserialize the contents as
        // Maps. Checking declared type parameters catches every such class in one rule.
        Class<?> valueClass = value.getClass();
        if (valueClass.getTypeParameters().length > 0) {
            throw new IllegalStateException("Step '" + stepDescription + "' is declared taskStoreState but produced a "
                    + valueClass.getName() + ", a generic type. Generic values such as List, Map, and Optional"
                    + " cannot be stored as STATE because Java erases their type arguments. Wrap the value in a"
                    + " domain class whose field keeps the element type, or use taskStoreResult so the step"
                    + " reloads on resume instead");
        }
        try {
            return new SerializedState(valueClass.getName(), objectMapper.valueToTree(value));
        } catch (Exception e) {
            throw new IllegalStateException("Step '" + stepDescription + "' is declared taskStoreState but its value"
                    + " of type " + valueClass.getName() + " is not serializable", e);
        }
    }

    /**
     * Restores a serialized durable value.
     * @param state the recorded state
     * @return the restored value, or null when it cannot be restored - the step then
     *         re-executes rather than replaying corrupt state
     */
    public Object deserialize(SerializedState state) {
        Object ret;
        try {
            Class<?> type = Class.forName(state.valueType());
            ret = objectMapper.treeToValue(state.value(), type);
        } catch (Exception e) {
            log.warn("Could not restore stored state of type {}, the step will re-execute", state.valueType(), e);
            ret = null;
        }
        return ret;
    }

}
