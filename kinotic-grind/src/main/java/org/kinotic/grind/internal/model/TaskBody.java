package org.kinotic.grind.internal.model;

import org.kinotic.grind.api.model.JobContext;
import org.kinotic.grind.api.model.Task;

/**
 * The work a {@link Task} performs, without the settings the task carries alongside it.
 *
 * @param <R> the result type
 */
@FunctionalInterface
public interface TaskBody<R> {

    /**
     * Performs the work, as {@link Task#execute(JobContext)} describes.
     * @param context the job scope for the run
     * @return the result
     */
    R execute(JobContext context) throws Exception;

}
