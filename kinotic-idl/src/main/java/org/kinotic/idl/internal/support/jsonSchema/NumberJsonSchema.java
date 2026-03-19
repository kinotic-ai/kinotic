

package org.kinotic.idl.internal.support.jsonSchema;

import java.util.Optional;

/**
 * The number type is used for any numeric type, either integers or floating point numbers.
 *
 * https://json-schema.org/understanding-json-schema/reference/numeric.html
 *
 *
 * Created by navid on 2019-06-11.
 */
public class NumberJsonSchema extends JsonSchema {

    /**
     * Numbers can be restricted to a multiple of a given number, using the multipleOf keyword.
     * It may be set to any positive number.
     *
     * https://json-schema.org/understanding-json-schema/reference/numeric.html#multiples
     */
    private Float multipleOf = null;

    /**
     * Ranges of numbers are specified using a combination of the minimum and maximum keywords, (or exclusiveMinimum and exclusiveMaximum for expressing exclusive range).
     * If x is the value being validated, the following must hold true:
     *
     *         x ≥ minimum
     *
     *         x > exclusiveMinimum
     *
     *         x ≤ maximum
     *
     *         x < exclusiveMaximum
     *
     * https://json-schema.org/understanding-json-schema/reference/numeric.html#range
     */
    private Float minimum = null;
    private Float exclusiveMinimum = null;

    private Float maximum = null;
    private Float exclusiveMaximum = null;



    public Optional<Float> getMultipleOf() {
        return Optional.ofNullable(multipleOf);
    }

    public NumberJsonSchema setMultipleOf(Float multipleOf) {
        this.multipleOf = multipleOf;
        return this;
    }

    public Optional<Float> getMinimum() {
        return Optional.ofNullable(minimum);
    }

    public NumberJsonSchema setMinimum(Float minimum) {
        this.minimum = minimum;
        return this;
    }

    public Optional<Float> getExclusiveMinimum() {
        return Optional.ofNullable(exclusiveMinimum);
    }

    public NumberJsonSchema setExclusiveMinimum(Float exclusiveMinimum) {
        this.exclusiveMinimum = exclusiveMinimum;
        return this;
    }

    public Optional<Float> getMaximum() {
        return Optional.ofNullable(maximum);
    }

    public NumberJsonSchema setMaximum(Float maximum) {
        this.maximum = maximum;
        return this;
    }

    public Optional<Float> getExclusiveMaximum() {
        return Optional.ofNullable(exclusiveMaximum);
    }

    public NumberJsonSchema setExclusiveMaximum(Float exclusiveMaximum) {
        this.exclusiveMaximum = exclusiveMaximum;
        return this;
    }

}
