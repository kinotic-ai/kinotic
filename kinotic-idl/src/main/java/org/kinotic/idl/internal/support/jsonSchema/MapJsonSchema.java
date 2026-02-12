

package org.kinotic.idl.internal.support.jsonSchema;

/**
 *
 * Created by navid on 2019-07-31.
 */
public class MapJsonSchema extends JsonSchema {

    private JsonSchema key;
    private JsonSchema value;

    public MapJsonSchema() {
    }

    public MapJsonSchema(JsonSchema key, JsonSchema value) {
        this.key = key;
        this.value = value;
    }

    public JsonSchema getKey() {
        return key;
    }

    public MapJsonSchema setKey(JsonSchema key) {
        this.key = key;
        return this;
    }

    public JsonSchema getValue() {
        return value;
    }

    public MapJsonSchema setValue(JsonSchema value) {
        this.value = value;
        return this;
    }
}
