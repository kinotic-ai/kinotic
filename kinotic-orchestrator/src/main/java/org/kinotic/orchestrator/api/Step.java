

package org.kinotic.orchestrator.api;

import org.reactivestreams.Publisher;
import org.springframework.context.support.GenericApplicationContext;

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
     * @param applicationContext the execution context that will be used for this {@link Step}
     * @param options the {@link ResultOptions} to use when executing the {@link JobDefinition}
     *               this will determine the {@link ResultType}'s that you will receive from the emitted {@link Result}'s
     * @return a {@link Publisher} that when subscribed to will create the result for this {@link Step}
     */
    Publisher<Result<?>> assemble(GenericApplicationContext applicationContext, ResultOptions options);

}
