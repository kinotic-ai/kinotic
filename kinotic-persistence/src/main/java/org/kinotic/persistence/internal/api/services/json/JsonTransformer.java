package org.kinotic.persistence.internal.api.services.json;

import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;

/**
 * Transforms the field at one json path as a {@link JsonStreamProcessor} streams it from input to output.
 * Created By Navíd Mitchell 🤪on 3/29/25
 *
 * @param <S> the state of the {@link JsonStreamProcessor} this transformer runs in
 */
@FunctionalInterface
public interface JsonTransformer<S extends JsonStreamProcessorState> {

    /**
     * Reads the field the parser is on and writes what the output holds in its place: the field as given, a
     * different value, additional fields, or nothing.
     *
     * @param parser    positioned on the field's {@code PROPERTY_NAME}; when this returns it must be on the
     *                  field value's last token
     * @param generator the output, positioned where the field belongs
     * @param state     the state of the current call
     */
    void transform(JsonParser parser, JsonGenerator generator, S state);
}
