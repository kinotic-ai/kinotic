/*
 *
 * Copyright 2008-2021 Kinotic and the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.kinotic.continuum.grind.internal.api;

import lombok.Getter;
import lombok.NoArgsConstructor;
import org.kinotic.continuum.grind.api.*;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.MapPropertySource;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Provides functionality for a {@link Step} that will execute a {@link JobDefinition}
 *
 *
 * Created by Navid Mitchell on 8/5/20
 */
public class JobDefinitionStep extends AbstractStep implements HasSteps {

    private static final Logger log = LoggerFactory.getLogger(JobDefinitionStep.class);

    private final JobDefinition jobDefinition;
    private final String taskDisplayString;

    public JobDefinitionStep(int sequence, JobDefinition jobDefinition) {
        super(sequence);
        this.jobDefinition = jobDefinition;
        this.taskDisplayString = "\"" + jobDefinition.getDescription() + "\"";
    }

    @Override
    public String getDescription() {
        return jobDefinition.getDescription();
    }

    @Override
    public Publisher<Result<?>> assemble(GenericApplicationContext applicationContext, ResultOptions options) {
        return Flux.create(sink -> {
            try {

                notifyProgress(() -> new Progress(0,
                                                  "JobDefinition: " + taskDisplayString + " Scope: " + jobDefinition.getScope() + " Executing"), sink, options, log);

                boolean cleanupContextOnFinallyDecision = false;

                GenericApplicationContext temp;
                if(jobDefinition.getScope() == JobScope.CHILD){
                    temp = createContext(applicationContext);
                }else if(jobDefinition.getScope() == JobScope.ISOLATED){
                    temp = createContext(null);
                    cleanupContextOnFinallyDecision = true;
                }else if(jobDefinition.getScope() == JobScope.PARENT){
                    temp = applicationContext;
                }else{
                    //noinspection ReactiveStreamsThrowInOperator
                    throw new IllegalStateException("Unknown JobDefinition Scope " + jobDefinition.getScope());
                }

                // Another var so it is "effectively" final..
                GenericApplicationContext contextToUse = temp;

                notifyDiagnostic(DiagnosticLevel.TRACE,
                                 () -> "JobDefinition: " +taskDisplayString + " Assembling Steps",
                                 sink, options, log);

                List<Publisher<Result<?>>> assembledTaskDefinitions = new ArrayList<>();

                for(Step step: jobDefinition.getSteps()){
                    assembledTaskDefinitions.add(step.assemble(contextToUse, options));
                }

                Flux<Result<?>> jobFlux;
                if(jobDefinition.isParallel()){
                    jobFlux = Flux.merge(assembledTaskDefinitions)
                              .parallel()
                              .runOn(Schedulers.parallel())
                              .sequential();
                }else{
                    jobFlux = Flux.concat(assembledTaskDefinitions);
                }

                int percentPerStep = !assembledTaskDefinitions.isEmpty() ? (int) Math.floor(100F / assembledTaskDefinitions.size()) : 100;
                ProgressHolder progressHolder = new ProgressHolder();
                // Another var so it is "effectively" final
                boolean cleanupContextOnFinally = cleanupContextOnFinallyDecision;
                Disposable disposable = jobFlux.doOnNext(result -> {
                                                    // notify progress at the job level as internal tasks complete
                                                    if(result.getResultType() == ResultType.PROGRESS){

                                                        Progress resultProgress = (Progress) result.getValue();
                                                        if(resultProgress.getPercentageComplete() < 100){

                                                            notifyProgress(() -> new Progress(progressHolder.getPercentageComplete(),
                                                                                              resultProgress.getMessage()), sink, options, log);

                                                        }else if(resultProgress.getPercentageComplete() == 100){

                                                            // progress of the JobDefinition is based on the total number of known sub tasks..
                                                            progressHolder.incrementPercentageComplete(percentPerStep);
                                                            notifyProgress(() -> new Progress(progressHolder.getPercentageComplete(),
                                                                                              resultProgress.getMessage()), sink, options, log);
                                                        }
                                                    }
                                                    result.getStepInfo().addAncestor(new StepInfo(sequence));
                                                    sink.next(result);
                                                })
                                               .doOnError(throwable -> {
                                                   notifyException(() -> "JobDefinition: " + taskDisplayString + " Exception during execution ", throwable, sink, options, log);
                                                   sink.error(throwable);
                                               })
                                               .doOnComplete(() -> {
                                                   notifyProgress(() -> new Progress(100,
                                                                                     "JobDefinition: " + taskDisplayString + " Finished Executing"), sink, options, log);
                                                   sink.complete();
                                               })
                                               .doFinally(signalType -> {
                                                   if(cleanupContextOnFinally) {
                                                       notifyDiagnostic(DiagnosticLevel.TRACE,
                                                                        () -> "JobDefinition: "+taskDisplayString+" Closing Job Execution Context",
                                                                        sink, options, log);

                                                       contextToUse.close();
                                                   }
                                               })
                                               .subscribe(); // TODO: not sure if warning is really an issue, but it should be investigated
                sink.onCancel(disposable);

            } catch (Exception throwable) {
                notifyException(() -> "JobDefinition: " + taskDisplayString + " Exception during execution ", throwable, sink, options, log);
                sink.error(throwable);
            }
        });
    }

    @Override
    public List<Step> getSteps() {
        return jobDefinition.getSteps();
    }

    private GenericApplicationContext createContext(GenericApplicationContext applicationContext){
        AnnotationConfigApplicationContext ret = new AnnotationConfigApplicationContext();
        ret.getEnvironment().getPropertySources().addLast(new MapPropertySource(GrindConstants.GRIND_MAP_PROPERTY_SOURCE, new HashMap<>()));
        if(applicationContext != null) {
            ret.setParent(applicationContext);
        }
        ret.refresh();
        return ret;
    }

    @Getter
    @NoArgsConstructor
    private static class ProgressHolder {
        private int percentageComplete = 0;

        public void incrementPercentageComplete(int progress){
            percentageComplete += progress;
            if(percentageComplete > 100){
                percentageComplete = 100;
            }
        }
    }

}
