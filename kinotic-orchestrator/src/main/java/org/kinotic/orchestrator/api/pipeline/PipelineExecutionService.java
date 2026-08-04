package org.kinotic.orchestrator.api.pipeline;

import org.kinotic.orchestrator.api.grind.ResultOptions;

/**
 * Executes registered {@link Pipeline}s, persisting a {@link org.kinotic.domain.api.model.grind.JobRun}
 * for the run and a {@link org.kinotic.domain.api.model.grind.TaskRecord} for every step executed.
 */
public interface PipelineExecutionService {

    /**
     * Prepares an execution of the named {@link Pipeline}.
     * The run starts, and its records are written, when the returned
     * {@link PipelineExecution#getResults()} is subscribed to.
     * @param pipelineName the registered name of the pipeline to execute
     * @return the prepared {@link PipelineExecution}
     * @throws IllegalArgumentException if no pipeline is registered under the given name
     */
    PipelineExecution execute(String pipelineName);

    /**
     * Prepares an execution of the named {@link Pipeline}.
     * The run starts, and its records are written, when the returned
     * {@link PipelineExecution#getResults()} is subscribed to.
     * @param pipelineName the registered name of the pipeline to execute
     * @param options the {@link ResultOptions} to use when executing
     * @return the prepared {@link PipelineExecution}
     * @throws IllegalArgumentException if no pipeline is registered under the given name
     */
    PipelineExecution execute(String pipelineName, ResultOptions options);

}
