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

import java.util.*;

/**
 * Objects are the mapping type in JSON. They map “keys” to “values”. In JSON, the “keys” must always be strings.
 * Each of these pairs is conventionally referred to as a “property”.
 *
 * https://json-schema.org/understanding-json-schema/reference/object.html
 *
 * TODO: add support for inheritance and composition outlined in the OpenAPI spec
 * https://swagger.io/specification/#schemaObject (allOf extension)
 * https://swagger.io/specification/#discriminatorObject
 *
 *
 *
 * Created by navid on 2019-06-11.
 */
public class ObjectJsonSchema extends JsonSchema {

    /**
     * The properties (key-value pairs) on an object are defined using the properties' keyword.
     * The value of properties is an object, where each key is the name of a property and each value is a JSON schema used to validate that property.
     *
     * https://json-schema.org/understanding-json-schema/reference/object.html#properties
     */
    private Map<String, JsonSchema> properties = new LinkedHashMap<>();

    /**
     * This is hard coded for now so it will always be false. The additional property validation is not very useful.
     */
    private final Boolean additionalProperties = Boolean.FALSE;

    /**
     * By default, the properties defined by the properties keyword are not required. However, one can provide a list of required properties using the required keyword.
     *
     * The required keyword takes an array of zero or more strings. Each of these strings must be unique.
     *
     * https://json-schema.org/understanding-json-schema/reference/object.html#required
     */
    private List<String> required = new LinkedList<>();

    /**
     * The names of properties can be validated against a schema, irrespective of their values.
     * This can be useful if you don’t want to enforce specific properties, but you want to make sure that the names of those properties follow a specific convention.
     * You might, for example, want to enforce that all names are valid ASCII tokens, so they can be used as attributes in a particular programming language.
     *
     * https://json-schema.org/understanding-json-schema/reference/object.html#propertynames
     */
    private StringJsonSchema propertyNames = null;

    /**
     * The number of properties on an object can be restricted using the minProperties and maxProperties keywords.
     * Each of these must be a non-negative integer.
     *
     * https://json-schema.org/understanding-json-schema/reference/object.html#size
     */
    private Integer minProperties = null;
    private Integer maxProperties = null;


    public ObjectJsonSchema addOptionalProperty(String name, JsonSchema schema){
        if(properties.containsKey(name)){
            throw new IllegalArgumentException(name+" property already exists");
        }
        properties.put(name, schema);
        return this;
    }

    public ObjectJsonSchema addRequiredProperty(String name, JsonSchema schema){
        if(properties.containsKey(name)){
            throw new IllegalArgumentException(name+" property already exists");
        }
        properties.put(name, schema);
        required.add(name);
        return this;
    }

    public Optional<StringJsonSchema> getPropertyNames() {
        return Optional.ofNullable(propertyNames);
    }

    public ObjectJsonSchema setPropertyNames(StringJsonSchema propertyNames) {
        this.propertyNames = propertyNames;
        return this;
    }

    public Optional<Integer> getMinProperties() {
        return Optional.ofNullable(minProperties);
    }

    public ObjectJsonSchema setMinProperties(Integer minProperties) {
        this.minProperties = minProperties;
        return this;
    }

    public Optional<Integer> getMaxProperties() {
        return Optional.ofNullable(maxProperties);
    }

    public ObjectJsonSchema setMaxProperties(Integer maxProperties) {
        this.maxProperties = maxProperties;
        return this;
    }

    public Boolean getAdditionalProperties() {
        return additionalProperties;
    }

    public Map<String, JsonSchema> getProperties() {
        return properties;
    }

    public ObjectJsonSchema setProperties(Map<String, JsonSchema> properties) {
        this.properties = properties;
        return this;
    }

    public List<String> getRequired() {
        return required;
    }

    public ObjectJsonSchema setRequired(List<String> required) {
        this.required = required;
        return this;
    }


}
