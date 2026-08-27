package org.kinotic.grind.internal.api.services;

import java.util.List;
import java.util.Objects;

/**
 * The wrapper pattern for storing a collection as state: the field's declared element type
 * survives erasure, so the batch round-trips where a bare List could not.
 */
public class WidgetBatch {

    public List<Widget> widgets;

    public WidgetBatch() {
    }

    public WidgetBatch(List<Widget> widgets) {
        this.widgets = widgets;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof WidgetBatch other && Objects.equals(widgets, other.widgets);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(widgets);
    }
}
