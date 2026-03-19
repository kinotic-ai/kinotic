package org.kinotic.persistence.internal.api.services.json;


import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;

/**
 * JsonTransformer is a functional interface that defines a transformer that can be applied to a given json path.
 * Created By Navíd Mitchell 🤪on 3/29/25
 */
public interface JsonTransformer {

    /**
     * The json path to apply this transformer to.
     * <p>
     * This is a json path expression that will be used to determine if this transformer should be applied to the current
     * json object or array of json objects.
     *
     * @return the json path expression
     */
    String appliesToJsonPath();

    /**
     * Process the json value at for the given json path.
     * @param generator the {@link JsonGenerator} to write the transformed json to
     * @param parser the {@link JsonParser} to read the json from
     * @param state the {@link JsonStreamProcessorState} that contains the current state of the json processing
     */
    void process(JsonGenerator generator,
                 JsonParser parser,
                 JsonStreamProcessorState state);

}
