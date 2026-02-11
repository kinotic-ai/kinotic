package org.kinotic.persistence.internal.api.services.json;

/**
 * JsonStreamProcessor is a functional interface that processes a json object or array of json objects.
 * @param <T> the type of the json object or array of json objects
 * @param <R> the type of the processed entity
 * @param <C> the type of the context that is provided by the user
 * Created by Navíd Mitchell 🤪 on 5/29/25.
 */
public interface JsonStreamProcessor<T, R, C> {

    /**
     * Processes a json object or array of json objects.
     * @param input to process, this can be any compatible json object or array of json objects. i.e. byte[], TokenBuffer, ect
     * @param context the context provides data provided by the user that may be needed to process the json
     * @return the processed entity
     */
    R process(T input, C context);


    static <T, R , C, S extends JsonStreamProcessorState>
    JsonStreamProcessor<T,R, C> create(JsonStreamProcessorStrategy<T, C, S> strategy) {
        return new DefaultJsonStreamProcessor<>(strategy);
    }
}
