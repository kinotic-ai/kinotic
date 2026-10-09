package org.kinotic.grind.api.model;

import org.kinotic.grind.api.model.events.TaskLogEvent;

/**
 * Writes a task's log: each line reaches watchers of the run as a {@link TaskLogEvent} and is kept
 * on the task's {@link TaskRecord} once the task finishes, which holds its last
 * {@value #MAX_RECORDED_ENTRIES} lines. Available in every job scope, so a task injects it like any
 * other dependency - an {@code @Autowired} field, or a parameter of an annotated task method.
 * Lines attach to the task in flight on the calling thread's Vert.x context - the run's own, where
 * the task body and the callbacks of futures bound to it execute - so a line written from a thread
 * the task spawned is dropped.
 *
 * Messages are formatted as SLF4J formats them: each {@code {}} is replaced by the next argument,
 * and a trailing {@link Throwable} with no {@code {}} of its own is appended to the message.
 */
public interface TaskLogger {

    /**
     * The number of a task's most recent lines its {@link TaskRecord} keeps.
     */
    int MAX_RECORDED_ENTRIES = 1000;

    /**
     * Writes a line of what the calling task did or found.
     * @param format the message, with a {@code {}} for each argument
     * @param arguments the values substituted into the message
     */
    void info(String format, Object... arguments);

    /**
     * Writes a line about something that needs attention but did not stop the calling task.
     * @param format the message, with a {@code {}} for each argument
     * @param arguments the values substituted into the message
     */
    void warn(String format, Object... arguments);

    /**
     * Writes a line about something that went wrong in the calling task.
     * @param format the message, with a {@code {}} for each argument
     * @param arguments the values substituted into the message
     */
    void error(String format, Object... arguments);

}
