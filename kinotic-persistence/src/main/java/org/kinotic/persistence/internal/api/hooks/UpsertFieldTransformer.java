package org.kinotic.persistence.internal.api.hooks;

import org.kinotic.idl.api.schema.decorators.C3Decorator;
import org.kinotic.persistence.internal.api.services.json.JsonTransformer;
import tools.jackson.core.JsonGenerator;

/**
 * Transforms a field carrying a given {@link C3Decorator} as an entity is saved or updated. Each json path of an
 * entity definition decorated with {@link #implementsDecorator()} is handed to this transformer.
 */
public interface UpsertFieldTransformer extends JsonTransformer<EntityUpsertState> {

    /**
     * @return the {@link C3Decorator} class whose fields this transformer handles
     */
    Class<? extends C3Decorator> implementsDecorator();

    /**
     * Writes the field with the given value, or with a json null when the value is null.
     */
    default void writeField(JsonGenerator generator, String fieldName, Object value) {
        if (value != null) {
            generator.writePOJOProperty(fieldName, value);
        } else {
            generator.writeNullProperty(fieldName);
        }
    }
}
