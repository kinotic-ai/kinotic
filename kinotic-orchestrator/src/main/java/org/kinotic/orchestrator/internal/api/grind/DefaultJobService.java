

package org.kinotic.orchestrator.internal.api.grind;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.Validate;
import org.kinotic.domain.api.services.JobRunService;
import org.kinotic.domain.api.services.TaskRecordService;
import org.kinotic.orchestrator.api.grind.DiagnosticLevel;
import org.kinotic.orchestrator.api.grind.JobDefinition;
import org.kinotic.orchestrator.api.grind.JobExecution;
import org.kinotic.orchestrator.api.grind.JobService;
import org.kinotic.orchestrator.api.grind.Result;
import org.kinotic.orchestrator.api.grind.ResultOptions;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import reactor.core.publisher.Flux;

import java.util.UUID;

/**
 *
 * Created by Navid Mitchell on 3/19/20
 */
@Component
@RequiredArgsConstructor
public class DefaultJobService implements JobService, ApplicationContextAware {

    private final JobRunService jobRunService;
    private final TaskRecordService taskRecordService;
    private final ObjectMapper objectMapper;

    private ConfigurableApplicationContext applicationContext;


    @Override
    public Flux<Result<?>> assemble(JobDefinition jobDefinition) {
        return assemble(jobDefinition, new ResultOptions(DiagnosticLevel.NONE, false));
    }

    @Override
    public Flux<Result<?>> assemble(JobDefinition jobDefinition, ResultOptions options) {
        Validate.notNull(jobDefinition, "JobDefinition Must not be null");
        Validate.notNull(options, "Options Must not be null");

        return Flux.defer(() -> {

            DefaultJobContext rootContext = new DefaultJobContext(applicationContext);

            JobDefinitionStep jobDefinitionStep = new JobDefinitionStep(0, jobDefinition);

            return Flux.from(jobDefinitionStep.assemble(rootContext, options))
                       .doFinally(signalType -> rootContext.destroy());
        });
    }

    @Override
    public JobExecution execute(JobDefinition jobDefinition) {
        return execute(jobDefinition, new ResultOptions(DiagnosticLevel.NONE, false));
    }

    @Override
    public JobExecution execute(JobDefinition jobDefinition, ResultOptions options) {
        Validate.notNull(jobDefinition, "JobDefinition Must not be null");
        Validate.notBlank(jobDefinition.getName(), "JobDefinition name must be set to execute");
        Validate.notNull(options, "Options Must not be null");

        String jobRunId = UUID.randomUUID().toString();

        JobRunRecorder recorder = new JobRunRecorder(jobRunId,
                                                     jobDefinition,
                                                     jobRunService,
                                                     taskRecordService,
                                                     objectMapper);

        Flux<Result<?>> results = assemble(jobDefinition, options)
                                      .doOnSubscribe(subscription -> recorder.runStarted())
                                      .doOnNext(recorder::record)
                                      .doOnError(recorder::runFailed)
                                      .doOnCancel(recorder::runCancelled)
                                      .doOnComplete(recorder::runCompleted);

        return new JobExecution(jobRunId, results);
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = (ConfigurableApplicationContext) applicationContext;
    }
}
