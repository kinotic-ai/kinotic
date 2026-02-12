

package org.kinotic.orchestrator.api;

import reactor.core.publisher.Flux;

/**
 *
 * Created by Navid Mitchell on 3/19/20
 */
public interface JobService {

    /**
     * Takes the given {@link JobDefinition} and assembles a {@link Flux} that when subscribed to will execute all of the {@link Task}'s within the {@link JobDefinition}
     * @param jobDefinition to assemble
     * @return the {@link Flux} that will execute the {@link JobDefinition}
     */
    Flux<Result<?>> assemble(JobDefinition jobDefinition);

    /**
     * Takes the given {@link JobDefinition} and assembles a {@link Flux} that when subscribed to will execute all of the {@link Task}'s within the {@link JobDefinition}
     * @param jobDefinition to assemble
     * @param options the {@link ResultOptions} to use when executing the {@link JobDefinition}
     *               this will determine the {@link ResultType}'s that you will receive from the emitted {@link Result}'s
     * @return the {@link Flux} that will execute the {@link JobDefinition}
     */
    Flux<Result<?>> assemble(JobDefinition jobDefinition, ResultOptions options);

}
