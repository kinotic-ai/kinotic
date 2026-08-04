package org.kinotic.orchestrator.internal.api.pipeline;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.Validate;
import org.kinotic.domain.api.services.JobRunService;
import org.kinotic.domain.api.services.TaskRecordService;
import org.kinotic.orchestrator.api.grind.DiagnosticLevel;
import org.kinotic.orchestrator.api.grind.JobDefinition;
import org.kinotic.orchestrator.api.grind.JobService;
import org.kinotic.orchestrator.api.grind.Result;
import org.kinotic.orchestrator.api.grind.ResultOptions;
import org.kinotic.orchestrator.api.pipeline.Pipeline;
import org.kinotic.orchestrator.api.pipeline.PipelineExecution;
import org.kinotic.orchestrator.api.pipeline.PipelineExecutionService;
import org.kinotic.orchestrator.api.pipeline.PipelineRegistry;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class DefaultPipelineExecutionService implements PipelineExecutionService {

    private final JobService jobService;
    private final PipelineRegistry pipelineRegistry;
    private final JobRunService jobRunService;
    private final TaskRecordService taskRecordService;
    private final ObjectMapper objectMapper;

    @Override
    public PipelineExecution execute(String pipelineName) {
        return execute(pipelineName, new ResultOptions(DiagnosticLevel.NONE, false));
    }

    @Override
    public PipelineExecution execute(String pipelineName, ResultOptions options) {
        Validate.notBlank(pipelineName, "pipelineName cannot be blank");
        Validate.notNull(options, "options cannot be null");

        Pipeline pipeline = pipelineRegistry.findByName(pipelineName);
        Validate.notNull(pipeline, "No Pipeline is registered with the name %s", pipelineName);

        JobDefinition jobDefinition = pipeline.createJobDefinition();
        String jobRunId = UUID.randomUUID().toString();

        JobRunRecorder recorder = new JobRunRecorder(jobRunId,
                                                     pipeline,
                                                     jobDefinition.getDescription(),
                                                     jobRunService,
                                                     taskRecordService,
                                                     objectMapper);

        Flux<Result<?>> results = jobService.assemble(jobDefinition, options)
                                            .doOnSubscribe(subscription -> recorder.runStarted())
                                            .doOnNext(recorder::record)
                                            .doOnError(recorder::runFailed)
                                            .doOnCancel(recorder::runCancelled)
                                            .doOnComplete(recorder::runCompleted);

        return new PipelineExecution(jobRunId, results);
    }

}
