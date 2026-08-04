

package org.kinotic.orchestrator.internal.api.grind;

import org.apache.commons.lang3.ClassUtils;
import org.kinotic.orchestrator.api.grind.*;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ReactiveAdapter;
import org.springframework.core.ReactiveAdapterRegistry;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

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
     * @param storeResult determines if the result of the {@link Task} should be stored in the execution scope
     * @param resultName the name of the result to use when storing the result in the execution scope
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
    public Publisher<Result<?>> assemble(JobContext context, ResultOptions options) {
        return Flux.create(sink -> {
            try {
                notifyStepStarted(task.getDescription(), sink);
                notifyProgress(() -> new Progress(0, "Task: " + taskDisplayString + " Executing"), sink, options, log);

                if(!(task instanceof NoopTask)) {

                    Object result = task.execute(context);

                    // check if this task returned a job definition, task, or something else
                    if(result instanceof JobDefinition){

                        completeWithJobDefinition(context, options, sink, (JobDefinition) result);

                    }else if(result instanceof Task){

                        completeWithTask(context, options, sink, (Task<?>) result);

                    }else{

                        completeWithResult(context, options, sink, result);

                    }
                }else{
                    log.debug("Task was noop {}", taskDisplayString);

                    sink.next(new DefaultResult<>(new StepInfo(sequence), ResultType.NOOP, null));
                    notifyStepCompleted(new StepCompletion(null, null), sink);
                    notifyProgress(() -> new Progress(100, "Task: " + taskDisplayString + " Finished Executing"), sink, options, log);
                    sink.complete();
                }
            } catch (Exception throwable) {
                notifyStepFailed(throwable, sink);
                notifyException(() -> "Task: " + taskDisplayString + " Exception during execution ", throwable, sink, options, log);
                sink.error(throwable);
            }
        });
    }

    private void completeWithJobDefinition(JobContext context,
                                           ResultOptions options,
                                           FluxSink<Result<?>> sink,
                                           JobDefinition jobDefinition){

        notifyDiagnostic(DiagnosticLevel.TRACE, () -> "Task: " + taskDisplayString + " returned a JobDefinition: \"" + jobDefinition.getDescription() + "\"", sink, options, log);

        JobDefinitionStep jobDefinitionStep = new JobDefinitionStep(1, jobDefinition);

        sink.next(new DefaultResult<>(new StepInfo(sequence), ResultType.DYNAMIC_STEPS, jobDefinitionStep));

        completeWithStep(options, sink, jobDefinitionStep.assemble(context, options));
    }

    private void completeWithTask(JobContext context,
                                  ResultOptions options,
                                  FluxSink<Result<?>> sink,
                                  Task<?> task) {

        notifyDiagnostic(DiagnosticLevel.TRACE, () -> "Task: " + taskDisplayString + " returned a Task: \"" + task.getDescription() + "\"", sink, options, log);

        TaskStep taskStep = new TaskStep(1, task, storeResult, resultName);

        sink.next(new DefaultResult<>(new StepInfo(sequence), ResultType.DYNAMIC_STEPS, taskStep));

        completeWithStep(options, sink, taskStep.assemble(context, options));
    }

    private void completeWithStep(ResultOptions options, FluxSink<Result<?>> sink, Publisher<Result<?>> assemble) {

        // Results are produced by Tasks that return a JobDefinition or a Task
        Disposable disposable = Flux.from(assemble)
                                    .doOnNext(result -> {
                                        result.getStepInfo().addAncestor(new StepInfo(sequence));
                                        sink.next(result);
                                    })
                                    .doOnError(throwable -> {
                                        notifyStepFailed(throwable, sink);
                                        notifyException(() -> "Task: " + taskDisplayString + " Exception during execution ", throwable, sink, options, log);
                                        sink.error(throwable);
                                    })
                                    .doOnComplete(() -> {
                                        // The dynamic step carries this step's storeResult settings, so it reports the stored value
                                        notifyStepCompleted(new StepCompletion(null, null), sink);
                                        notifyProgress(() -> new Progress(100, "Task: " + taskDisplayString + " Finished Executing"),
                                                       sink, options, log);
                                        sink.complete();
                                    })
                                    .subscribe();
        sink.onCancel(disposable);
    }

    private void completeWithResult(JobContext context,
                                    ResultOptions options,
                                    FluxSink<Result<?>> sink,
                                    Object result){
        if (result != null) {
            // Check if result is reactive if so we only complete once result is complete
            ReactiveAdapter reactiveAdapter = reactiveAdapterRegistry.getAdapter(null, result);
            if(reactiveAdapter != null){

                notifyDiagnostic(DiagnosticLevel.TRACE, () -> "Task: " + taskDisplayString+ " returned value of type:\"" + result.getClass().getName(), sink, options, log);

                // Holds the last stored value so STEP_COMPLETED can report it once the publisher finishes
                AtomicReference<Object> lastStoredValue = new AtomicReference<>();

                Disposable disposable = Flux.from(reactiveAdapter.toPublisher(result))
                    .doOnNext(value -> {

                        // If the value returned is a Result type we will store it but the forward through
                        // we just overwrite the parentIdentifier to match this task
                        if(value instanceof Result<?> resultInternal){
                            if(resultInternal.getResultType() == ResultType.VALUE){
                                storeIfDesired(context, options, sink, resultInternal.getValue());
                                lastStoredValue.set(resultInternal.getValue());
                            }
                            resultInternal.getStepInfo().addAncestor(new StepInfo(sequence));
                            sink.next(resultInternal);
                        }else{
                            storeIfDesired(context, options, sink, value);
                            lastStoredValue.set(value);
                            sink.next(new DefaultResult<>(new StepInfo(sequence), ResultType.VALUE, value));
                        }

                    }).doOnError(throwable -> {

                        notifyStepFailed(throwable, sink);
                        notifyException(() -> "Task: " + taskDisplayString + " Exception during execution ", throwable, sink, options, log);
                        sink.error(throwable);

                    }).doOnComplete(() -> {

                        notifyStepCompleted(new StepCompletion(storeResult ? resultName : null,
                                                               storeResult ? lastStoredValue.get() : null), sink);
                        notifyProgress(() -> new Progress(100, "Task: " + taskDisplayString + " Finished Executing"), sink, options, log);
                        sink.complete();

                    }).subscribe();

                sink.onCancel(disposable);

            } else {
                storeIfDesired(context, options, sink, result);

                sink.next(new DefaultResult<>(new StepInfo(sequence), ResultType.VALUE, result));

                notifyStepCompleted(new StepCompletion(storeResult ? resultName : null,
                                                       storeResult ? result : null), sink);

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

            notifyStepCompleted(new StepCompletion(storeResult ? resultName : null, null), sink);

            notifyProgress(() -> new Progress(100, "Task: " + taskDisplayString + " Finished Executing"), sink, options, log);

            sink.complete();
        }
    }

    private void storeIfDesired(JobContext context,
                                ResultOptions options,
                                FluxSink<Result<?>> sink,
                                Object result){
        if(storeResult) {

            if (result != null) {

                if (isBeanCandidate(result)) {
                    if (result instanceof Collection) {

                        if(this.resultName != null && !this.resultName.isEmpty()){
                            notifyDiagnostic(DiagnosticLevel.TRACE,
                                             () -> "Task: " + taskDisplayString + " Storing result as Collection Property \"" + resultName + "\" Value: " + result,
                                             sink, options, log);

                            context.storeProperty(resultName, result);

                        }else{
                            for (Object val : ((Collection<?>) result)) {

                                String beanName = val.getClass().getSimpleName() + "_" + UUID.randomUUID();

                                notifyDiagnostic(DiagnosticLevel.TRACE,
                                                 () -> "Task: " + taskDisplayString + " Storing result as Singleton: \"" + beanName + "\" Value: " + result,
                                                 sink, options, log);

                                context.storeBean(beanName, val);
                             }
                        }
                    } else {
                        String beanName = this.resultName != null && !this.resultName.isEmpty() ? this.resultName : result.getClass().getSimpleName();

                        notifyDiagnostic(DiagnosticLevel.TRACE,
                                         () -> "Task: " + taskDisplayString + " Storing result as Singleton: \"" + beanName + "\" Value: " + result,
                                         sink, options, log);

                        context.storeBean(beanName, result);
                    }

                } else {

                    if (resultName != null && !resultName.isEmpty()) {
                        notifyDiagnostic(DiagnosticLevel.TRACE,
                                         () -> "Task: " + taskDisplayString + " Storing result as Property: \"" + resultName + "\" Value: " + result,
                                         sink, options, log);

                        context.storeProperty(resultName, result);
                    } else {

                        notifyDiagnostic(DiagnosticLevel.WARN,
                                         () -> "Task: " + taskDisplayString +" Cannot store Job Scope Property. All primitive types must have a name defined.",
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
