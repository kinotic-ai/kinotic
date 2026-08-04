package org.kinotic.orchestrator.api.pipeline;

/**
 * Registry of the {@link Pipeline}s known to this orchestrator, keyed by name.
 */
public interface PipelineRegistry {

    /**
     * Finds the {@link Pipeline} registered under the given name.
     * @param name of the pipeline to find
     * @return the {@link Pipeline} or null if none is registered under the given name
     */
    Pipeline findByName(String name);

}
