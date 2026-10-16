package org.kinotic.grind.api.model;

import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * A unit of work executed as one task of a {@link JobDefinition}, and how a console presents it:
 * its icon and the {@link TaskView} it shows as it starts, runs and completes; while it runs, the
 * view is replaced through the scope's {@link TaskReporter}, and the latest view is the one its
 * record keeps. A task is an immutable value:
 * the fluent methods return copies, so one instance can serve several definitions, or a step and
 * another step's {@link Store#reload(Task) reload task}, without either seeing the other's settings.
 */
public interface Task<T> {

    /**
     * @return the description of this {@link Task} shown in run records
     */
    String getDescription();

    /**
     * @return how this {@link Task}'s result is kept, or null when it declares no {@link Store} of
     *         its own: a task added to a definition then keeps nothing, and a dynamic task keeps its
     *         result as the task that produced it declares
     */
    Store getStore();

    /**
     * @return the key of the icon a console shows for this {@link Task}, one of {@link TaskIcons}
     *         or another the console knows, or null for its default
     */
    String getIcon();

    /**
     * @param store how the result is kept: its {@link StoreType} governs resume behavior, its name
     *              places the value in the job scope for later tasks, and its wire flag publishes
     *              the value to watchers of the run
     * @return a copy of this {@link Task} that keeps its result as the given {@link Store} declares
     */
    Task<T> store(Store store);

    /**
     * @param icon the key of the icon a console shows for the task, see {@link #getIcon()}
     * @return a copy of this {@link Task} shown with the given icon
     */
    Task<T> icon(String icon);

    /**
     * Declares the view a console shows for the task from the moment it starts, for following
     * what the task does while it runs. Just before the task executes, the value of the given type
     * is looked up in the job scope the way {@code @Autowired} looks it up, and the producer builds
     * the view from it; a value the scope does not hold fails the task.
     * @param source the type of the scope value the view is built from
     * @param producer builds the view from that value
     * @param <S> the source type
     * @return a copy of this {@link Task} showing the view from its start
     */
    <S> Task<T> startView(Class<S> source, Function<S, TaskView<?>> producer);

    /**
     * Declares the view a console shows for the task once it completes, built from its result: the
     * value it returns, or the value a returned {@code Future}, {@code CompletionStage} or
     * {@code Publisher} completes with. The view is reported as the task's last view, as
     * {@link TaskReporter#view(TaskView)} reports one, and the task then completes with its result
     * unchanged. A result of another type than the producer takes fails the task.
     * @param producer builds the view from the task's result
     * @param <V> the type of the task's result
     * @return a copy of this {@link Task} showing the view on completion
     */
    <V> Task<T> resultView(Function<V, TaskView<?>> producer);

    /**
     * As {@link #resultView(Function)}, the producer also given the value of the given type, looked
     * up in the job scope as {@link #startView(Class, Function)} looks it up.
     * @param source the type of the scope value the view is also built from
     * @param producer builds the view from the task's result and that value
     * @param <V> the type of the task's result
     * @param <S> the source type
     * @return a copy of this {@link Task} showing the view on completion
     */
    <V, S> Task<T> resultView(Class<S> source, BiFunction<V, S, TaskView<?>> producer);

    /**
     * Performs the work of this {@link Task}.
     * <p>
     * An asynchronous result belongs in the return value, or is awaited in place with the Vert.x
     * {@code Future}'s {@code await()} - both suspend the run's context so the work queued on it
     * keeps flowing. Blocking on a future with {@code CompletableFuture.get()} or {@code join()}
     * instead parks the thread while it still holds the context: a future bound to the run's
     * context, as every platform repository and service returns, delivers its completion through
     * that context and so hangs the run permanently, and a raw library future holds up the run's
     * queued record writes and event delivery until it completes.
     * @param context the job scope for the run
     * @return the result of this {@link Task}. A returned Vert.x {@code Future},
     *         {@code CompletionStage}, or {@code Publisher} is awaited and its value used.
     *         A returned {@link Task} or {@link JobDefinition} is executed as dynamically
     *         discovered tasks. Any other value is the task's result.
     */
    T execute(JobContext context) throws Exception;

}
