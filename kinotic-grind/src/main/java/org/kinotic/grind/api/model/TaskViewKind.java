package org.kinotic.grind.api.model;

import org.apache.commons.lang3.Validate;

/**
 * A kind of {@link TaskView} a console knows how to render, binding its key to the type of data
 * it renders, so a view of one kind cannot be built with another kind's data.
 *
 * @param name the key of the component that renders views of this kind
 * @param <T>  the type of data views of this kind render
 */
public record TaskViewKind<T>(String name) {

    public TaskViewKind {
        Validate.notBlank(name, "name cannot be blank");
    }

    /**
     * @param data what the component renders
     * @return a view of this kind showing the given data
     */
    public TaskView<T> of(T data) {
        return new TaskView<>(name, data);
    }

}
