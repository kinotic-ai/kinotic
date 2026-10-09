package org.kinotic.grind.api.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.Date;

/**
 * One line a task wrote through its {@link TaskLogger}.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class TaskLogEntry {

    /**
     * When the task wrote the line.
     */
    private Date timestamp;

    /**
     * The line's severity.
     */
    private TaskLogLevel level;

    /**
     * The text of the line.
     */
    private String message;

}
