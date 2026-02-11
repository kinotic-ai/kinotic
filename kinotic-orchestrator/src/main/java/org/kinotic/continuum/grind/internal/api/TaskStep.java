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

import org.apache.commons.lang3.ClassUtils;
import org.kinotic.continuum.grind.api.*;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.ReactiveAdapter;
import org.springframework.core.ReactiveAdapterRegistry;
import org.springframework.core.env.MapPropertySource;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.UUID;

/**
 * Provides functionality for a {@link Step} that will execute a {@link Task} that will emit a single value
 *
 *
 * Created by Navid Mitchell on 3/19/20
 */
public class TaskStep extends AbstractStep {

    private static final Logger log = LoggerFactory.getLogger(TaskStep.class);

    private final ReactiveAdapterRegistry reactiveAdapterRegistry;
    private final Task<?> task;
    private final boolean storeResult;
    private final String resultName;
    private final String taskDisplayString;

    public TaskStep(int sequence, Task<?> task) {
        this(sequence, task, false, null);
    }

    public TaskStep(int sequence, Task<?> task, boolean storeResult) {
        this(sequence, task, storeResult, null);
    }

    /**
     * Create a {@link Step} that will execute a {@link Task} that will emit a single value
     * @param task for this step
     * @param storeResult determines if the result of the {@link Task} should be stored in the execution context
     * @param resultName the name of the result to use when storing the result in the execution context
     */
    public TaskStep(int sequence,
                    Task<?> task,
                    boolean storeResult,
                    String resultName) {
        super(sequence);
        this.task = task;
        this.storeResult = storeResult;
        this.resultName = resultName;
        this.taskDisplayString = "\"" + task.getDescription() + "\"";

        reactiveAdapterRegistry = ReactiveAdapterRegistry.getSharedInstance();
    }

    @Override
    public String getDescription() {
        return task.getDescription();
    }

    @Override
    public Publisher<Result<?>> assemble(GenericApplicationContext applicationContext, ResultOptions options) {
        return Flux.create(sink -> {
            try {
                notifyProgress(() -> new Progress(0, "Task: " + taskDisplayString + " Executing"), sink, options, log);

                if(!(task instanceof NoopTask)) {

                    Object result = task.execute(applicationContext);

                    // check if this task returned a job definition, task, or something else
                    if(result instanceof JobDefinition){

                        completeWithJobDefinition(applicationContext, options, sink, (JobDefinition) result);

                    }else if(result instanceof Task){

                        completeWithTask(applicationContext, options, sink, (Task<?>) result);

                    }else{

                        completeWithResult(applicationContext, options, sink, result);

                    }
                }else{
                    log.debug("Task was noop {}", taskDisplayString);

                    sink.next(new DefaultResult<>(new StepInfo(sequence), ResultType.NOOP, null));
                    notifyProgress(() -> new Progress(100, "Task: " + taskDisplayString + " Finished Executing"), sink, options, log);
                    sink.complete();
                }
            } catch (Exception throwable) {
                notifyException(() -> "Task: " + taskDisplayString + " Exception during execution ", throwable, sink, options, log);
                sink.error(throwable);
            }
        });
    }

    private void completeWithJobDefinition(GenericApplicationContext applicationContext,
                                           ResultOptions options,
                                           FluxSink<Result<?>> sink,
                                           JobDefinition jobDefinition){

        notifyDiagnostic(DiagnosticLevel.TRACE, () -> "Task: " + taskDisplayString + " returned a JobDefinition: \"" + jobDefinition.getDescription() + "\"", sink, options, log);

        JobDefinitionStep jobDefinitionStep = new JobDefinitionStep(1, jobDefinition);

        sink.next(new DefaultResult<>(new StepInfo(sequence), ResultType.DYNAMIC_STEPS, jobDefinitionStep));

        completeWithStep(options, sink, jobDefinitionStep.assemble(applicationContext, options));
    }

    private void completeWithTask(GenericApplicationContext applicationContext,
                                  ResultOptions options,
                                  FluxSink<Result<?>> sink,
                                  Task<?> task) {

        notifyDiagnostic(DiagnosticLevel.TRACE, () -> "Task: " + taskDisplayString + " returned a Task: \"" + task.getDescription() + "\"", sink, options, log);

        TaskStep taskStep = new TaskStep(1, task, storeResult, resultName);

        sink.next(new DefaultResult<>(new StepInfo(sequence), ResultType.DYNAMIC_STEPS, taskStep));

        completeWithStep(options, sink, taskStep.assemble(applicationContext, options));
    }

    private void completeWithStep(ResultOptions options, FluxSink<Result<?>> sink, Publisher<Result<?>> assemble) {

        // Results are produced by Tasks that return a JobDefinition or a Task
        Disposable disposable = Flux.from(assemble)
                                    .doOnNext(result -> {
                                        result.getStepInfo().addAncestor(new StepInfo(sequence));
                                        sink.next(result);
                                    })
                                    .doOnError(throwable -> {
                                        notifyException(() -> "Task: " + taskDisplayString + " Exception during execution ", throwable, sink, options, log);
                                        sink.error(throwable);
                                    })
                                    .doOnComplete(() -> {
                                        notifyProgress(() -> new Progress(100, "Task: " + taskDisplayString + " Finished Executing"),
                                                       sink, options, log);
                                        sink.complete();
                                    })
                                    .subscribe();
        sink.onCancel(disposable);
    }

    private void completeWithResult(GenericApplicationContext applicationContext,
                                    ResultOptions options,
                                    FluxSink<Result<?>> sink,
                                    Object result){
        if (result != null) {
            // Check if result is reactive if so we only complete once result is complete
            ReactiveAdapter reactiveAdapter = reactiveAdapterRegistry.getAdapter(null, result);
            if(reactiveAdapter != null){

                notifyDiagnostic(DiagnosticLevel.TRACE, () -> "Task: " + taskDisplayString+ " returned value of type:\"" + result.getClass().getName(), sink, options, log);

                Disposable disposable = Flux.from(reactiveAdapter.toPublisher(result))
                    .doOnNext(value -> {

                        // If the value returned is a Result type we will store it but the forward through
                        // we just overwrite the parentIdentifier to match this task
                        if(value instanceof Result<?> resultInternal){
                            if(resultInternal.getResultType() == ResultType.VALUE){
                                addIfDesiredToApplicationContext(applicationContext, options, sink, resultInternal.getValue());
                            }
                            resultInternal.getStepInfo().addAncestor(new StepInfo(sequence));
                            sink.next(resultInternal);
                        }else{
                            addIfDesiredToApplicationContext(applicationContext, options, sink, value);
                            sink.next(new DefaultResult<>(new StepInfo(sequence), ResultType.VALUE, value));
                        }

                    }).doOnError(throwable -> {

                        notifyException(() -> "Task: " + taskDisplayString + " Exception during execution ", throwable, sink, options, log);
                        sink.error(throwable);

                    }).doOnComplete(() -> {

                        notifyProgress(() -> new Progress(100, "Task: " + taskDisplayString + " Finished Executing"), sink, options, log);
                        sink.complete();

                    }).subscribe();

                sink.onCancel(disposable);

            } else {
                addIfDesiredToApplicationContext(applicationContext, options, sink, result);

                sink.next(new DefaultResult<>(new StepInfo(sequence), ResultType.VALUE, result));

                notifyProgress(() -> new Progress(100, "Task: " + taskDisplayString + " Finished Executing"), sink, options, log);

                sink.complete();
            }
        }else{
            if(storeResult) {
                notifyDiagnostic(DiagnosticLevel.WARN,
                                 () -> "Task: " + taskDisplayString + " Result was requested to be stored, but result is NULL",
                                 sink,
                                 options,
                                 log);
            }

            sink.next(new DefaultResult<>(new StepInfo(sequence), ResultType.VALUE, null));

            notifyProgress(() -> new Progress(100, "Task: " + taskDisplayString + " Finished Executing"), sink, options, log);

            sink.complete();
        }
    }

    private void addIfDesiredToApplicationContext(GenericApplicationContext applicationContext,
                                                  ResultOptions options,
                                                  FluxSink<Result<?>> sink,
                                                  Object result){
        if(storeResult) {

            if (result != null) {

                Class<?> clazz = result.getClass();
                ConfigurableBeanFactory beanFactory = applicationContext.getBeanFactory();

                MapPropertySource propertySource = (MapPropertySource) applicationContext.getEnvironment()
                                                                                         .getPropertySources()
                                                                                         .get(GrindConstants.GRIND_MAP_PROPERTY_SOURCE);
                // sanity check
                if (propertySource == null) {
                    throw new IllegalStateException("Expected MapPropertySource was not set for " + GrindConstants.GRIND_MAP_PROPERTY_SOURCE);
                }

                if (isBeanCandidate(result)) {
                    if (result instanceof Collection) {

                        if(this.resultName != null && !this.resultName.isEmpty()){
                            notifyDiagnostic(DiagnosticLevel.TRACE,
                                             () -> "Task: " + taskDisplayString + " Storing result as Collection Property \"" + resultName + "\" Value: " + result,
                                             sink, options, log);

                            propertySource.getSource().put(resultName, result);

                        }else{
                            for (Object val : ((Collection<?>) result)) {

                                String beanName = val.getClass().getSimpleName() + "_" + UUID.randomUUID();

                                notifyDiagnostic(DiagnosticLevel.TRACE,
                                                 () -> "Task: " + taskDisplayString + " Storing result as Singleton: \"" + beanName + "\" Value: " + result,
                                                 sink, options, log);

                                beanFactory.registerSingleton(beanName, val);
                             }
                        }
                    } else {
                        String beanName = this.resultName != null && !this.resultName.isEmpty() ? this.resultName : clazz.getSimpleName();

                        notifyDiagnostic(DiagnosticLevel.TRACE,
                                         () -> "Task: " + taskDisplayString + " Storing result as Singleton: \"" + beanName + "\" Value: " + result,
                                         sink, options, log);

                        beanFactory.registerSingleton(beanName, result);
                    }

                } else {

                    if (resultName != null && !resultName.isEmpty()) {
                        notifyDiagnostic(DiagnosticLevel.TRACE,
                                         () -> "Task: " + taskDisplayString + " Storing result as Property: \"" + resultName + "\" Value: " + result,
                                         sink, options, log);

                        propertySource.getSource().put(resultName, result);
                    } else {

                        notifyDiagnostic(DiagnosticLevel.WARN,
                                         () -> "Task: " + taskDisplayString +" Cannot store Application Context Property. All primitive types must have a name defined.",
                                         sink, options, log);
                    }
                }
            }else{

                notifyDiagnostic(DiagnosticLevel.WARN,
                                 () -> "Task: " + taskDisplayString +" Result was requested to be stored, but result is NULL",
                                 sink, options, log);
            }
        }
    }

    private boolean isBeanCandidate(Object result){
        boolean ret = false;
        Class<?> clazz = result.getClass();
        if(!clazz.isArray()
                && !clazz.isEnum()
                && !ClassUtils.isPrimitiveOrWrapper(clazz)
                && !clazz.isAnnotation()
                && !(result instanceof CharSequence)
                && !(result instanceof Date)
                && !(result instanceof Calendar)){
            ret = true;
        }
        return ret;
    }


}
