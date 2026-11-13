package org.kinotic.persistence.internal.api.services.json;

import java.util.List;

/**
 * Streams json from an input to an output in a single pass, handing the fields at configured json paths to
 * {@link JsonTransformer}s and turning each root object into a result, as its {@link JsonStreamProcessorStrategy}
 * directs. Processing is synchronous and failures are thrown.
 * @param <T> the type of the input, i.e. byte[], TokenBuffer
 * @param <C> the type of the context the caller provides
 * @param <E> the type each root object becomes
 * Created by Navíd Mitchell 🤪 on 5/29/25.
 */
public interface JsonStreamProcessor<T, C, E> {

    /**
     * Processes an input holding a single root object.
     * @param input to process
     * @param context the data the caller provides for processing
     * @return what the root object became
     * @throws IllegalStateException if the input does not hold exactly one root object
     */
    E process(T input, C context);

    /**
     * Processes an input holding a json array of root objects, or a single root object.
     * @param input to process
     * @param context the data the caller provides for processing
     * @return what each root object became, in input order
     */
    List<E> processArray(T input, C context);

    static <T, C, S extends JsonStreamProcessorState, E>
    JsonStreamProcessor<T, C, E> create(JsonStreamProcessorStrategy<T, C, S, E> strategy) {
        return new DefaultJsonStreamProcessor<>(strategy);
    }
}
