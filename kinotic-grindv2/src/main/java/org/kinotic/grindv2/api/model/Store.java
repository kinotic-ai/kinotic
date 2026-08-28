package org.kinotic.grindv2.api.model;

import lombok.Getter;
import org.apache.commons.lang3.Validate;

/**
 * Declares how a {@link Task}'s result is kept: the {@link StoreType} governing resume
 * behavior, the name it is stored under in the job scope, an optional reload {@link Task} for
 * result stores, and whether the value is published to watchers of the run. Instances are
 * immutable, so a {@code Store} can be shared between definitions.
 */
@Getter
public class Store {

    private static final Store NONE = new Store(StoreType.NONE, null, null, false);

    /**
     * How the result is kept across a resume.
     */
    private final StoreType type;

    /**
     * The name the result is stored under in the job scope, or null to derive one from the
     * value.
     */
    private final String name;

    /**
     * Executed instead of the task when a resumed run finds it already completed, or null if
     * the task is its own reload. Only set on {@link StoreType#RESULT} stores.
     */
    private final Task<?> reloadTask;

    /**
     * True if the completed value is published to watchers of the run: serialized as JSON onto
     * the run's {@code TaskCompletedEvent} and {@code TaskRecord}.
     */
    private final boolean wire;

    private Store(StoreType type, String name, Task<?> reloadTask, boolean wire) {
        this.type = type;
        this.name = name;
        this.reloadTask = reloadTask;
        this.wire = wire;
    }

    /**
     * Keeps nothing: the task's value never enters the job scope and is not replayed on
     * resume. Equivalent to adding the task without a {@code Store}. Combine with
     * {@link #wire()} for a value that exists only for watchers of the run.
     * @return the store
     */
    public static Store none() {
        return NONE;
    }

    /**
     * Stores the result in the job scope under a name derived from the value. On resume the
     * task re-executes to regenerate it, so it must be safe to re-run.
     * @return the store
     */
    public static Store result() {
        return new Store(StoreType.RESULT, null, null, false);
    }

    /**
     * Stores the result in the job scope under the given name, as {@link #result()}.
     * @param name to store the result under
     * @return the store
     */
    public static Store result(String name) {
        Validate.notBlank(name, "name cannot be blank");
        return new Store(StoreType.RESULT, name, null, false);
    }

    /**
     * Stores the result as durable state under a name derived from the value: the value is
     * serialized into the run's {@code TaskRecord}, and on resume it is replayed from the
     * record instead of executing the task again. See {@link StoreType#STATE} for what values
     * qualify.
     * @return the store
     */
    public static Store state() {
        return new Store(StoreType.STATE, null, null, false);
    }

    /**
     * Stores durable state under the given name, as {@link #state()}.
     * @param name to store the result under
     * @return the store
     */
    public static Store state(String name) {
        Validate.notBlank(name, "name cannot be blank");
        return new Store(StoreType.STATE, name, null, false);
    }

    /**
     * Pairs this result store with the {@link Task} that reloads the value from its source of
     * truth: when a resumed run finds the task already completed, the reload task executes
     * instead, so the creation is never repeated.
     * @param reloadTask executed on resume to reload the value
     * @return a store with the reload task
     * @throws IllegalStateException if this is not a {@link StoreType#RESULT} store - durable
     *         state replays from the record and never reloads
     */
    public Store reload(Task<?> reloadTask) {
        Validate.notNull(reloadTask, "reloadTask cannot be null");
        Validate.validState(type == StoreType.RESULT,
                            "reload applies only to result stores, %s replays from the record", type);
        return new Store(type, name, reloadTask, wire);
    }

    /**
     * Publishes the completed value to watchers of the run, serialized as JSON onto the run's
     * {@code TaskCompletedEvent} and persisted on its {@code TaskRecord}. Independent of the
     * {@link StoreType}: a result store can publish without being durable, durable state stays
     * private unless it opts in, and a {@link #none()} store can publish a value that exists
     * only for observers - never entering the job scope, and gone when a resume skips the
     * task. The value must be JSON-serializable.
     * @return a store that publishes its value
     */
    public Store wire() {
        return new Store(type, name, reloadTask, true);
    }

}
