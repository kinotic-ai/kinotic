

package org.kinotic.orchestrator.internal.api;

import org.apache.commons.lang3.Validate;
import org.kinotic.orchestrator.api.DiagnosticLevel;
import org.kinotic.orchestrator.api.JobDefinition;
import org.kinotic.orchestrator.api.JobService;
import org.kinotic.orchestrator.api.Result;
import org.kinotic.orchestrator.api.ResultOptions;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Flux;

/**
 *
 * Created by Navid Mitchell on 3/19/20
 */
@Component
public class DefaultJobService implements JobService, ApplicationContextAware {

    private GenericApplicationContext applicationContext;


    @Override
    public Flux<Result<?>> assemble(JobDefinition jobDefinition) {
        return assemble(jobDefinition, new ResultOptions(DiagnosticLevel.NONE, false));
    }

    @Override
    public Flux<Result<?>> assemble(JobDefinition jobDefinition, ResultOptions options) {
        Validate.notNull(jobDefinition, "JobDefinition Must not be null");
        Validate.notNull(options, "Options Must not be null");

        return Flux.defer(() -> {

            JobDefinitionStep jobDefinitionStep = new JobDefinitionStep(0, jobDefinition);

            return jobDefinitionStep.assemble(applicationContext, options);
        });
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = (GenericApplicationContext) applicationContext;
    }
}
