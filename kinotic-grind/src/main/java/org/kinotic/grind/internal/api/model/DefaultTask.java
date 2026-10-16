package org.kinotic.grind.internal.api.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.Validate;
import org.kinotic.grind.api.model.JobContext;
import org.kinotic.grind.api.model.Store;
import org.kinotic.grind.api.model.Task;
import org.kinotic.grind.api.model.TaskView;
import org.kinotic.grind.internal.model.TaskBody;
import org.kinotic.grind.internal.model.ViewProducer;

import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * The {@link Task} every factory builds: a {@link TaskBody} with the settings the fluent methods
 * give it, each of which returns a copy.
 *
 * @param <R> the result type
 */
@RequiredArgsConstructor
public final class DefaultTask<R> implements Task<R> {

    @Getter
    private final String description;

    @Getter
    private final Store store;

    @Getter
    private final String icon;

    /**
     * The view shown from the task's start, or null when it declares none.
     */
    @Getter
    private final ViewProducer startView;

    /**
     * The view shown once the task completes, or null when it declares none.
     */
    @Getter
    private final ViewProducer resultView;

    private final TaskBody<R> body;

    /**
     * Creates a task that declares nothing beyond its description and work.
     * @param description of the task
     * @param body the work it performs
     * @param <R> the result type
     * @return the task
     */
    public static <R> DefaultTask<R> of(String description, TaskBody<R> body) {
        Validate.notNull(body, "body cannot be null");
        return new DefaultTask<>(description, null, null, null, null, body);
    }

    @Override
    public Task<R> store(Store store) {
        Validate.notNull(store, "store cannot be null");
        return new DefaultTask<>(description, store, icon, startView, resultView, body);
    }

    @Override
    public Task<R> icon(String icon) {
        return new DefaultTask<>(description, store, icon, startView, resultView, body);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <S> Task<R> startView(Class<S> source, Function<S, TaskView<?>> producer) {
        Validate.notNull(source, "source cannot be null");
        Validate.notNull(producer, "producer cannot be null");
        ViewProducer view = new ViewProducer(source, (result, value) -> producer.apply((S) value));
        return new DefaultTask<>(description, store, icon, view, resultView, body);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <V> Task<R> resultView(Function<V, TaskView<?>> producer) {
        Validate.notNull(producer, "producer cannot be null");
        ViewProducer view = new ViewProducer(null, (result, value) -> producer.apply((V) result));
        return new DefaultTask<>(description, store, icon, startView, view, body);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <V, S> Task<R> resultView(Class<S> source, BiFunction<V, S, TaskView<?>> producer) {
        Validate.notNull(source, "source cannot be null");
        Validate.notNull(producer, "producer cannot be null");
        ViewProducer view = new ViewProducer(source, (result, value) -> producer.apply((V) result, (S) value));
        return new DefaultTask<>(description, store, icon, startView, view, body);
    }

    @Override
    public R execute(JobContext context) throws Exception {
        return body.execute(context);
    }

}
