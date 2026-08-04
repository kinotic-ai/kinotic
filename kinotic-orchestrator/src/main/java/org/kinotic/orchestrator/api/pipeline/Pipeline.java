package org.kinotic.orchestrator.api.pipeline;

import org.kinotic.orchestrator.api.grind.JobDefinition;

/**
 * A named, versioned recipe for a grind job. Implementations are Spring beans and are
 * collected into the {@link PipelineRegistry} at startup, making them executable by name
 * through the {@link PipelineExecutionService}.
 */
public interface Pipeline {

    /**
     * The name this pipeline is registered and executed under.
     * Must be unique across all registered pipelines.
     * @return the pipeline name
     */
    String getName();

    /**
     * The version of this pipeline's definition, recorded with every run so a persisted
     * run can be matched to the definition that produced it.
     * @return the pipeline version
     */
    String getVersion();

    /**
     * Builds the {@link JobDefinition} for one execution of this pipeline.
     * Called once per run, so the returned definition must be freshly constructed.
     * @return the {@link JobDefinition} to execute
     */
    JobDefinition createJobDefinition();

}
