package org.kinotic.grind.api.model;

import org.apache.commons.lang3.Validate;

/**
 * What a console renders in a task's detail pane: the component named by {@link #kind()}, given
 * {@link #data()}. Built through a {@link TaskViewKind}, which fixes the data's type. The data
 * reaches every watcher of the run, so it must never carry secrets, and it must serialize to JSON.
 *
 * @param kind the key of the component that renders the view
 * @param data what the component renders
 * @param <T>  the data's type
 */
public record TaskView<T>(String kind, T data) {

    public TaskView {
        Validate.notBlank(kind, "kind cannot be blank");
    }

}
