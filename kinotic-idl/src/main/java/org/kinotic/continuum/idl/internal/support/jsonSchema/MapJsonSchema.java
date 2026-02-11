/*
 *
 * Copyright 2008-2021 Kinotic and the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.kinotic.continuum.idl.internal.support.jsonSchema;

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
