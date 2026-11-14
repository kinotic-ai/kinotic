package org.kinotic.persistence.internal.api.services.json;

import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;

import java.util.Map;

/**
 * Supplies what a {@link JsonStreamProcessor} does with one kind of json: where it reads from and writes to,
 * which {@link JsonTransformer} handles which json path, and what each root object becomes.
 * Created By Navíd Mitchell 🤪on 3/29/25
 *
 * @param <T> the type of the input
 * @param <C> the type of the context the caller provides
 * @param <S> the type of the state carried through one call
 * @param <E> the type each root object becomes
 */
public interface JsonStreamProcessorStrategy<T, C, S extends JsonStreamProcessorState, E> {

    /**
     * @return the transformer for each json path it handles; a field at any other path is copied unchanged
     */
    Map<String, JsonTransformer<S>> getTransformers();

    /**
     * @return a new state for one call with the given context
     */
    S createState(C context);

    /**
     * @return a parser over the input
     */
    JsonParser createParser(T input);

    /**
     * @return the generator the root objects are written to for the call holding this state
     */
    JsonGenerator createGenerator(S state);

    /**
     * Called before a root object's {@code END_OBJECT} is written, to validate the object and to write fields
     * that depend on what the object did not hold.
     */
    void onRootObjectEnd(JsonGenerator generator, S state);

    /**
     * Called once a root object is written and flushed.
     *
     * @return what the root object becomes
     */
    E emit(S state);

    /**
     * Called once after the last root object of the input.
     */
    void onComplete(S state);
}
