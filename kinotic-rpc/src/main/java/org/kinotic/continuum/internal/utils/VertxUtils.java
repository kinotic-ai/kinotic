package org.kinotic.continuum.internal.utils;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Created by Navíd Mitchell 🤪on 6/16/23.
 */
public class VertxUtils {

    /**
     * Converts a {@link JsonObject} to a flat {@link Map} where the keys are the path to the value
     * @param jsonObject to convert
     * @return a flat map of the json object
     */
    public static Map<String, String> jsonObjectToFlatMap(JsonObject jsonObject) {
        Map<String, String> ret = new LinkedHashMap<>();
        jsonObject.forEach(entry -> {
            if (entry.getValue() instanceof JsonObject) {
                Map<String, String> subMap = jsonObjectToFlatMap((JsonObject) entry.getValue());
                subMap.forEach((key, value) -> ret.put(entry.getKey() + "." + key, value));
            } else if (entry.getValue() instanceof JsonArray){
                throw new IllegalArgumentException("JsonArray's are not supported");
            } else {
                ret.put(entry.getKey(), entry.getValue().toString());
            }
        });
        return ret;
    }

}
