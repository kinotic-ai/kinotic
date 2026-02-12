

package org.kinotic.orchestrator.api;

import org.springframework.context.support.GenericApplicationContext;

/**
 * A general definition of a task that can be executed at some point in the future.
 *
 *
 * Created by Navid Mitchell 🤪 on 3/24/20
 */
public interface Task<T> {

    /**
     * @return the description of this {@link Task}
     */
    String getDescription();

    /**
     * This method needs to perform the logic that actually returns the value created by this {@link Task} if any.
     * @param applicationContext the execution context for this job
     * @return the result of this {@link Task}
     *         This can be any value or any of the following which will be handled with special consideration.
     *         Result can be another {@link Task} in this case the {@link Task} will be executed and the result will be handled according to these same rules
     *         Result can be a {@link JobDefinition} in this case the {@link JobDefinition} will be executed and all results will be handled according to these rules
     *         Result can be a {@link Result} object in this case the {@link Result} will be returned along with any other {@link Result}'s during task execution
     *
     */
    T execute(GenericApplicationContext applicationContext) throws Exception;

}
