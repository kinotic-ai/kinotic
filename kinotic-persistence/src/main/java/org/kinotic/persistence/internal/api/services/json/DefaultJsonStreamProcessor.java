package org.kinotic.persistence.internal.api.services.json;

import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

/**
 * Created By Navíd Mitchell 🤪on 3/29/25
 */
public class DefaultJsonStreamProcessor<T, C, S extends JsonStreamProcessorState, E> implements JsonStreamProcessor<T, C, E> {

    private final JsonStreamProcessorStrategy<T, C, S, E> strategy;
    private final Map<String, JsonTransformer<S>> transformers;

    public DefaultJsonStreamProcessor(JsonStreamProcessorStrategy<T, C, S, E> strategy) {
        this.strategy = strategy;
        this.transformers = strategy.getTransformers();
    }

    @Override
    public E process(T input, C context) {
        List<E> results = doProcess(input, context, false);
        if(results.size() != 1){
            throw new IllegalStateException("Expected exactly one entity to be returned, got " + results.size());
        }
        return results.getFirst();
    }

    @Override
    public List<E> processArray(T input, C context) {
        return doProcess(input, context, true);
    }

    private List<E> doProcess(T input, C context, boolean processArray){
        Deque<String> jsonPathStack = new ArrayDeque<>();
        List<E> ret = new ArrayList<>();
        int arrayDepth = 0;
        S state = strategy.createState(context);

        // closing the generator returns its buffers to Jackson's recycler for the next call to reuse
        try(JsonParser jsonParser = strategy.createParser(input);
            JsonGenerator jsonGenerator = strategy.createGenerator(state)) {

            while (jsonParser.nextToken() != null) {

                JsonToken token = jsonParser.currentToken();
                int objectDepth = state.getObjectDepth();

                if (token == JsonToken.PROPERTY_NAME) {

                    String fieldName = jsonParser.currentName();

                    // if the stack depth and the object depth are the same we are at a new field in the same object so pop the stack
                    if(jsonPathStack.size() == objectDepth){
                        jsonPathStack.removeFirst();
                    }

                    String currentJsonPath = !jsonPathStack.isEmpty() ? jsonPathStack.peekFirst() + "." + fieldName : fieldName;
                    jsonPathStack.addFirst(currentJsonPath);

                    JsonTransformer<S> transformer = transformers.get(currentJsonPath);
                    if(transformer != null){
                        transformer.transform(jsonParser, jsonGenerator, state);
                    }else{
                        jsonGenerator.copyCurrentEvent(jsonParser);
                    }

                }else{

                    if(objectDepth == 0){
                        requireRootToken(token, arrayDepth, processArray);
                    }

                    if(token == JsonToken.END_OBJECT && objectDepth == 1){
                        strategy.onRootObjectEnd(jsonGenerator, state);
                        jsonGenerator.writeEndObject();
                        jsonGenerator.flush();
                        ret.add(strategy.emit(state));
                    }else if(objectDepth > 0 || token == JsonToken.START_OBJECT){
                        // outside a root object the only other tokens are the enclosing array's, which is dropped
                        // so each root object is written on its own
                        jsonGenerator.copyCurrentEvent(jsonParser);
                    }

                    if(token == JsonToken.START_OBJECT){
                        state.setObjectDepth(objectDepth + 1);
                    }else if(token == JsonToken.END_OBJECT){

                        // if the stack depth and the object depth are the same, the last field is done so pop the stack
                        if(jsonPathStack.size() == objectDepth){
                            jsonPathStack.removeFirst();
                        }

                        state.setObjectDepth(objectDepth - 1);
                    }

                    if(token == JsonToken.START_ARRAY){
                        arrayDepth++;
                    }else if(token == JsonToken.END_ARRAY){
                        arrayDepth--;
                    }
                }
            }
        }

        strategy.onComplete(state);
        return ret;
    }

    // Outside a root object the input may hold only the start of one, and in array mode the array enclosing them
    private void requireRootToken(JsonToken token, int arrayDepth, boolean processArray){
        if(processArray){
            boolean valid = (token == JsonToken.START_ARRAY && arrayDepth == 0)
                    || (token == JsonToken.START_OBJECT && arrayDepth == 1)
                    || (token == JsonToken.END_ARRAY && arrayDepth == 1);
            if(!valid){
                throw new IllegalArgumentException("Expected a json array of objects");
            }
        }else if(token != JsonToken.START_OBJECT){
            throw new IllegalArgumentException("Expected a json object");
        }
    }
}
