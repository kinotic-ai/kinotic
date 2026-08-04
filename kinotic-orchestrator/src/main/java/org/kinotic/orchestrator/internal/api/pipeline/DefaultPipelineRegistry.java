package org.kinotic.orchestrator.internal.api.pipeline;

import org.kinotic.orchestrator.api.pipeline.Pipeline;
import org.kinotic.orchestrator.api.pipeline.PipelineRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Collects every {@link Pipeline} bean at startup, failing fast on duplicate names.
 */
@Component
public class DefaultPipelineRegistry implements PipelineRegistry {

    private final Map<String, Pipeline> pipelinesByName = new HashMap<>();

    public DefaultPipelineRegistry(ObjectProvider<Pipeline> pipelines) {
        pipelines.forEach(pipeline -> {
            Pipeline existing = pipelinesByName.putIfAbsent(pipeline.getName(), pipeline);
            if(existing != null){
                throw new IllegalStateException("Multiple Pipelines registered with the name " + pipeline.getName()
                                                + ": " + existing.getClass().getName() + " and " + pipeline.getClass().getName());
            }
        });
    }

    @Override
    public Pipeline findByName(String name) {
        return pipelinesByName.get(name);
    }

}
