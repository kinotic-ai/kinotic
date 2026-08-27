package org.kinotic.grind.internal.api.services;

import java.util.Objects;

/**
 * Round-trippable value stored by tasks under test.
 */
public class Widget {

    public String label;

    public Widget() {
    }

    public Widget(String label) {
        this.label = label;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Widget other && Objects.equals(label, other.label);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(label);
    }
}
