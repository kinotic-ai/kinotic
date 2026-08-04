package org.kinotic.orchestrator.api.pipeline;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.kinotic.orchestrator.api.grind.Result;
import reactor.core.publisher.Flux;

/**
 * One execution of a {@link Pipeline}, pairing the id of its persistent
 * {@link org.kinotic.domain.api.model.grind.JobRun} record with the {@link Result} stream
 * that performs the run.
 */
@Getter
@RequiredArgsConstructor
public class PipelineExecution {

    /**
     * The id of the {@link org.kinotic.domain.api.model.grind.JobRun} recorded for this execution.
     */
    private final String jobRunId;

    /**
     * The stream that executes the pipeline when subscribed to.
     * Must be subscribed exactly once - each subscription would execute the pipeline
     * again under the same {@link #jobRunId}.
     */
    private final Flux<Result<?>> results;

}
