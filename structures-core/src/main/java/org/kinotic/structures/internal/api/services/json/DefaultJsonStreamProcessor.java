package org.kinotic.structures.internal.api.services.json;

import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * Created By Navíd Mitchell 🤪on 3/29/25
 */
public class DefaultJsonStreamProcessor<T, R, C, S extends JsonStreamProcessorState> implements JsonStreamProcessor<T, R, C> {

    private final JsonStreamProcessorStrategy<T, C, S> strategy;
    private final Map<String, JsonTransformer> transformers = new HashMap<>();

    public DefaultJsonStreamProcessor(JsonStreamProcessorStrategy<T, C, S> strategy) {
        this.strategy = strategy;
        for(JsonTransformer transformer : strategy.getTransformers()){
            transformers.put(transformer.appliesToJsonPath(), transformer);
        }
    }

    @Override
    public R process(T input, C context) {
        boolean processArray = false;
        Deque<String> jsonPathStack = new ArrayDeque<>();
        int objectDepth = 0;
        int arrayDepth = 0;

        S state = strategy.createState(context);

        try(JsonParser jsonParser = strategy.createParser(input, state)) {
            JsonGenerator jsonGenerator = strategy.createGenerator(input, state);

            while (jsonParser.nextToken() != null) {

                JsonToken token = jsonParser.currentToken();

                if (token == JsonToken.PROPERTY_NAME) {

                    String fieldName = jsonParser.currentName();

                    // if the stack depth and the object depth are the same we are at a new field in the same object so pop the stack
                    if(jsonPathStack.size() == objectDepth){
                        jsonPathStack.removeFirst();
                    }

                    String currentJsonPath = !jsonPathStack.isEmpty() ? jsonPathStack.peekFirst() + "." + fieldName : fieldName;
                    jsonPathStack.addFirst(currentJsonPath);

                    JsonTransformer transformer = transformers.get(currentJsonPath);

                    // Apply preProcessor if any exist
                    if(transformer != null){
                        transformer.process(jsonGenerator, jsonParser, state);
                    }else{
                        jsonGenerator.copyCurrentEvent(jsonParser);
                    }

                }else{

                    // End of root level object
                    if(token == JsonToken.END_OBJECT && objectDepth == 1){


                    }else{
                        if(!shouldSkipToken(token, jsonParser.currentValue(), arrayDepth, processArray)){
                            jsonGenerator.copyCurrentEvent(jsonParser);
                        }
                    }

                    if(token == JsonToken.START_OBJECT){
                        objectDepth++;
                    }else if(token == JsonToken.END_OBJECT){

                        // if the stack depth and the object depth are the same, the last field is done so pop the stack
                        if(jsonPathStack.size() == objectDepth){
                            jsonPathStack.removeFirst();
                        }

                        objectDepth--;
                    }

                    if(token == JsonToken.START_ARRAY){
                        arrayDepth++;
                    }else if(token == JsonToken.END_ARRAY){
                        arrayDepth--;
                    }
                }
            }

            return null;

        } catch (Exception e) {
            throw new IllegalArgumentException("Error processing JSON", e);
        }
    }

    private boolean shouldSkipToken(JsonToken token, Object currentValue, int arrayDepth, boolean processArray){
        boolean ret = false;
        if(processArray) {
            if (token == JsonToken.START_ARRAY && arrayDepth == 0) {
                ret = true;
            } else if (token == JsonToken.END_ARRAY && arrayDepth == 1) {
                ret = true;
            } else if (arrayDepth == 1 && (  ",".equals(currentValue) || " ".equals(currentValue) )) {
                ret = true;
            }
        }
        return ret;
    }

}
