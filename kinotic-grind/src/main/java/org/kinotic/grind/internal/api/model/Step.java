

package org.kinotic.grind.internal.api.model;

import org.kinotic.grind.api.model.JobContext;
import org.kinotic.grind.api.model.JobDefinition;
import org.kinotic.grind.api.model.Result;
import org.kinotic.grind.api.model.ResultOptions;
import org.kinotic.grind.api.model.ResultType;
import org.kinotic.grind.api.model.Task;
import org.reactivestreams.Publisher;

/**
 * A step in a {@link JobDefinition}
 *
 * Created by Navid Mitchell on 3/25/20
 */
public interface Step {

    /**
     * This is the sequence for this step in the {@link JobDefinition} the first {@link Task} would create a {@link Step} with a sequence of one and so on
     * @return the sequence for this step
     */
    int getSequence();

    /**
     * The description comes from the {@link Task} or {@link JobDefinition}
     * that this step was created for
     * @return the description of this {@link Step}
     */
    String getDescription();

    /**
     * Prepares the {@link Step} for execution.
     *
     * @param stepPath the {@code /} separated sequence path locating this {@link Step} within the run
     * @param context the execution scope that will be used for this {@link Step}
     * @param options the {@link ResultOptions} to use when executing the {@link JobDefinition}
     *               this will determine the {@link ResultType}'s that you will receive from the emitted {@link Result}'s
     * @param replayLedger the completed steps of the run being resumed, or null when this is not a resume
     * @return a {@link Publisher} that when subscribed to will create the result for this {@link Step}
     */
    Publisher<Result<?>> assemble(String stepPath, JobContext context, ResultOptions options, ReplayLedger replayLedger);

}
