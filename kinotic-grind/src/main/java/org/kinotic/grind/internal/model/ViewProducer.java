package org.kinotic.grind.internal.model;

import org.kinotic.grind.api.model.TaskView;

import java.util.function.BiFunction;

/**
 * A view a task declares: built from the task's result, null for a view shown from the task's
 * start, and from the scope value of the source type, when there is one.
 *
 * @param source   the type of the scope value the view is built from, or null for none
 * @param producer builds the view from the result and the scope value
 */
public record ViewProducer(Class<?> source, BiFunction<Object, Object, TaskView<?>> producer) {
}
