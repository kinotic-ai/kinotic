package org.kinotic.auth.api.expressions;

/**
 * A literal value in a policy expression: string, integer, decimal, or boolean.
 */
public record LiteralValue(Object value, LiteralType type) implements Operand {

    public enum LiteralType {
        STRING,
        INTEGER,
        DECIMAL,
        BOOLEAN
    }

    public String asString() {
        return (String) value;
    }

    public long asLong() {
        return (Long) value;
    }

    public double asDouble() {
        return (Double) value;
    }

    public boolean asBoolean() {
        return (Boolean) value;
    }
}
