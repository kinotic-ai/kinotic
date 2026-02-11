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

import java.util.Optional;

/**
 * The string type is used for strings of text. It may contain Unicode characters.
 * <p>
 * https://json-schema.org/understanding-json-schema/reference/string.html
 * <p>
 *
 *     TODO: support enum keyword through the JsonSchema using generic keywords or here directly https://swagger.io/docs/specification/data-models/enums/
 *
 *
 * Created by navid on 2019-06-11.
 */
public class StringJsonSchema extends JsonSchema {

    /**
     * The length of a string can be constrained using the minLength and maxLength keywords. For both keywords, the value must be a non-negative number.
     * <p>
     * https://json-schema.org/understanding-json-schema/reference/string.html#length
     */
    private Integer minLength = null;
    private Integer maxLength = null;

    /**
     * The pattern keyword is used to restrict a string to a particular regular expression.
     * The regular expression syntax is the one defined in JavaScript (ECMA 262 specifically).
     * See Regular Expressions for more information. ( https://json-schema.org/understanding-json-schema/reference/regular_expressions.html#regular-expressions )
     * <p>
     * https://json-schema.org/understanding-json-schema/reference/string.html#pattern
     */
    private String pattern = null;

    /**
     * The format keyword allows for basic semantic validation on certain kinds of string values that are commonly used.
     * This allows values to be constrained beyond what the other tools in JSON Schema, including Regular Expressions can do.
     * <p>
     * https://json-schema.org/understanding-json-schema/reference/string.html#format
     */
    private String format = null;



    public Optional<Integer> getMinLength() {
        return Optional.ofNullable(minLength);
    }

    public StringJsonSchema setMinLength(Integer minLength) {
        this.minLength = minLength;
        return this;
    }

    public Optional<Integer> getMaxLength() {
        return Optional.ofNullable(maxLength);
    }

    public StringJsonSchema setMaxLength(Integer maxLength) {
        this.maxLength = maxLength;
        return this;
    }

    public Optional<String> getPattern() {
        return Optional.ofNullable(pattern);
    }

    public StringJsonSchema setPattern(String pattern) {
        this.pattern = pattern;
        return this;
    }

    public Optional<String> getFormat() {
        return Optional.ofNullable(format);
    }

    public StringJsonSchema setFormat(String format) {
        this.format = format;
        return this;
    }

}
