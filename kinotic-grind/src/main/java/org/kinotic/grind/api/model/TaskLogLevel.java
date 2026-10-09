package org.kinotic.grind.api.model;

/**
 * The severity of a {@link TaskLogEntry}.
 */
public enum TaskLogLevel {

    /**
     * What the task did or found.
     */
    INFO,

    /**
     * Something the task found that needs attention but did not stop it.
     */
    WARN,

    /**
     * Something that went wrong in the task.
     */
    ERROR

}
