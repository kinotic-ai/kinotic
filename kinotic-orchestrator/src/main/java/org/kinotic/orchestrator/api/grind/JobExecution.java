package org.kinotic.orchestrator.api.grind;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;

/**
 * One recorded execution of a {@link JobDefinition}, pairing the id of its persistent
 * {@link org.kinotic.domain.api.model.grind.JobRun} record with the {@link Result} stream
 * that performs the run.
 */
@Getter
@RequiredArgsConstructor
public class JobExecution {

    /**
     * The id of the {@link org.kinotic.domain.api.model.grind.JobRun} recorded for this execution.
     */
    private final String jobRunId;

    /**
     * The stream that executes the job when subscribed to.
     * Must be subscribed exactly once - each subscription would execute the job
     * again under the same {@link #jobRunId}.
     */
    private final Flux<Result<?>> results;

}
