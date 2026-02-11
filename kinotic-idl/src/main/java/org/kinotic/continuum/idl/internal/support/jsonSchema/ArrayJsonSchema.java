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

import java.util.LinkedList;
import java.util.Optional;

/**
 * Arrays are used for ordered elements. In JSON, each element in an array may be of a different type.
 * <p>
 * https://json-schema.org/understanding-json-schema/reference/array.html
 * <p>
 *
 * Created by navid on 2019-06-11.
 */
public class ArrayJsonSchema extends JsonSchema {

    /**
     * Items:
     * <p>
     * By default, the elements of the array may be anything at all.
     * However, it’s often useful to validate the items of the array against some schema as well.
     * This is done using the items, additionalItems, and contains keywords.
     * <p>
     * There are two ways in which arrays are generally used in JSON:
     * <p>
     * List validation: is useful for arrays of arbitrary length where each item matches the same schema.
     * For this kind of array, set the items keyword to a single schema that will be used to validate all the items in the array.
     * <p>
     * Tuple validation: is useful when the array is a collection of items where each has a different schema and the ordinal index of each item is meaningful.
     * To do this, we set the items keyword to an array, where each item is a schema that corresponds to each index of the document’s array.
     * That is, an array where the first element validates the first element of the input array, the second element validates the second element of the input array, etc.
     * <p>
     * https://json-schema.org/understanding-json-schema/reference/array.html#items
     */
    private LinkedList<JsonSchema> items = new LinkedList<>();

    /**
     * While the items schema must be valid for every item in the array, the contains schema only needs to validate against one or more items in the array.
     * <p>
     * https://json-schema.org/understanding-json-schema/reference/array.html#contains
     */
    private JsonSchema contains = null;

    /**
     * This is hard coded for now so it will always be false.
     * <p>
     * https://json-schema.org/understanding-json-schema/reference/array.html#additionalItems
     *
     * TODO: this is possibly more useful than the ObjectJsonSchema counterpart and should probably be implemented...
     */
    private final Boolean additionalItems = Boolean.FALSE;

    /**
     * The length of the array can be specified using the minItems and maxItems keywords.
     * The value of each keyword must be a non-negative number.
     * These keywords work whether doing List validation or Tuple validation.
     * <p>
     * https://json-schema.org/understanding-json-schema/reference/array.html#length
     */
    private Integer minItems = null;
    private Integer maxItems = null;

    /**
     * A schema can ensure that each of the items in an array is unique. Simply set the uniqueItems keyword to true.
     * <p>
     * https://json-schema.org/understanding-json-schema/reference/array.html#uniqueness
     */
    private Boolean uniqueItems = null;


    public ArrayJsonSchema addItem(JsonSchema item){
        items.add(item);
        return this;
    }

    public LinkedList<JsonSchema> getItems() {
        return items;
    }

    public ArrayJsonSchema setItems(LinkedList<JsonSchema> items) {
        this.items = items;
        return this;
    }

    public Optional<JsonSchema> getContains() {
        return Optional.ofNullable(contains);
    }

    public ArrayJsonSchema setContains(JsonSchema contains) {
        this.contains = contains;
        return this;
    }

    public Boolean getAdditionalItems() {
        return additionalItems;
    }

    public Optional<Integer> getMinItems() {
        return Optional.ofNullable(minItems);
    }

    public ArrayJsonSchema setMinItems(Integer minItems) {
        this.minItems = minItems;
        return this;
    }

    public Optional<Integer> getMaxItems() {
        return Optional.ofNullable(maxItems);
    }

    public ArrayJsonSchema setMaxItems(Integer maxItems) {
        this.maxItems = maxItems;
        return this;
    }

    public Optional<Boolean> getUniqueItems() {
        return Optional.ofNullable(uniqueItems);
    }

    public ArrayJsonSchema setUniqueItems(Boolean uniqueItems) {
        this.uniqueItems = uniqueItems;
        return this;
    }

}
