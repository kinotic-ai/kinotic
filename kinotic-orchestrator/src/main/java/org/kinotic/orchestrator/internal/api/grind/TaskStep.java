

package org.kinotic.orchestrator.internal.api.grind;

import org.apache.commons.lang3.ClassUtils;
import org.kinotic.orchestrator.api.model.grind.StoreType;
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
    private final Task<?> reloadTask;
    private final StoreType storeType;
    private final String resultName;
    private final String taskDisplayString;

    public TaskStep(int sequence, Task<?> task) {
        this(sequence, task, null, StoreType.NONE, null);
    }

    /**
     * Create a {@link Step} that will execute a {@link Task} that will emit a single value
     * @param task for this step
     * @param reloadTask executed instead of {@code task} when a resumed run finds this step already
     *                   completed, or null if the task is its own reload
     * @param storeType determines how the result of the {@link Task} is stored in the execution scope
     * @param resultName the name of the result to use when storing the result in the execution scope
     */
    public TaskStep(int sequence,
                    Task<?> task,
                    Task<?> reloadTask,
                    StoreType storeType,
                    String resultName) {
        super(sequence);
        this.task = task;
        this.reloadTask = reloadTask;
        this.storeType = storeType;
        this.resultName = resultName;
        this.taskDisplayString = "\"" + task.getDescription() + "\"";

        reactiveAdapterRegistry = ReactiveAdapterRegistry.getSharedInstance();
    }

    @Override
    public String getDescription() {
        return task.getDescription();
    }

    @Override
    public Publisher<Result<?>> assemble(String stepPath, JobContext context, ResultOptions options, ReplayLedger replayLedger) {
        return Flux.create(sink -> {
            try {
                // A ledger entry means the original run completed this step. A step that produced
                // dynamic steps must re-execute so they are regenerated; the regenerated steps then
                // consult the ledger at their own paths.
                ReplayEntry replayEntry = replayLedger != null ? replayLedger.get(stepPath) : null;
                boolean completedInOriginalRun = replayEntry != null && !replayEntry.isDynamicSteps();

                if(completedInOriginalRun && storeType == StoreType.NONE){
                    notifyStepStarted(task.getDescription(), sink);
                    notifyStepCompleted(new StepCompletion(StoreType.NONE, null, null), sink);
                    notifyProgress(() -> new Progress(100, "Task: " + taskDisplayString + " already completed, skipped"), sink, options, log);
                    sink.complete();
                    return;
                }

                if(completedInOriginalRun && storeType == StoreType.STATE && replayEntry.getValue() != null){
                    notifyStepStarted(task.getDescription(), sink);
                    storeIfDesired(context, options, sink, replayEntry.getValue());
                    notifyStepCompleted(new StepCompletion(storeType, resultName, replayEntry.getValue()), sink);
                    notifyProgress(() -> new Progress(100, "Task: " + taskDisplayString + " already completed, replayed stored state"), sink, options, log);
                    sink.complete();
                    return;
                }

                // RESULT reloads from its source of truth: the declared reload task when there is one,
                // otherwise the task itself, which must then be safe to re-run
                Task<?> taskToRun = completedInOriginalRun && reloadTask != null ? reloadTask : task;

                notifyStepStarted(task.getDescription(), sink);
                notifyProgress(() -> new Progress(0, "Task: " + taskDisplayString + " Executing"), sink, options, log);

                if(!(taskToRun instanceof NoopTask)) {

                    Object result = taskToRun.execute(context);

                    // check if this task returned a job definition, task, or something else
                    if(result instanceof JobDefinition){

                        completeWithJobDefinition(stepPath, context, options, replayLedger, sink, (JobDefinition) result);

                    }else if(result instanceof Task){

                        completeWithTask(stepPath, context, options, replayLedger, sink, (Task<?>) result);

                    }else{

                        completeWithResult(context, options, sink, result);

                    }
                }else{
                    log.debug("Task was noop {}", taskDisplayString);

                    sink.next(new DefaultResult<>(new StepInfo(sequence), ResultType.NOOP, null));
                    // a noop stored nothing, whatever this step's declared StoreType
                    notifyStepCompleted(new StepCompletion(StoreType.NONE, null, null), sink);
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

    private void completeWithJobDefinition(String stepPath,
                                           JobContext context,
                                           ResultOptions options,
                                           ReplayLedger replayLedger,
                                           FluxSink<Result<?>> sink,
                                           JobDefinition jobDefinition){

        notifyDiagnostic(DiagnosticLevel.TRACE, () -> "Task: " + taskDisplayString + " returned a JobDefinition: \"" + jobDefinition.getDescription() + "\"", sink, options, log);

        JobDefinitionStep jobDefinitionStep = new JobDefinitionStep(1, jobDefinition);

        sink.next(new DefaultResult<>(new StepInfo(sequence), ResultType.DYNAMIC_STEPS, jobDefinitionStep));

        completeWithStep(options, sink, jobDefinitionStep.assemble(stepPath + "/" + jobDefinitionStep.getSequence(), context, options, replayLedger));
    }

    private void completeWithTask(String stepPath,
                                  JobContext context,
                                  ResultOptions options,
                                  ReplayLedger replayLedger,
                                  FluxSink<Result<?>> sink,
                                  Task<?> task) {

        notifyDiagnostic(DiagnosticLevel.TRACE, () -> "Task: " + taskDisplayString + " returned a Task: \"" + task.getDescription() + "\"", sink, options, log);

        TaskStep taskStep = new TaskStep(1, task, reloadTask, storeType, resultName);

        sink.next(new DefaultResult<>(new StepInfo(sequence), ResultType.DYNAMIC_STEPS, taskStep));

        completeWithStep(options, sink, taskStep.assemble(stepPath + "/" + taskStep.getSequence(), context, options, replayLedger));
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
                                        // The dynamic step carries this step's store settings, so it reports the stored value
                                        notifyStepCompleted(new StepCompletion(StoreType.NONE, null, null), sink);
                                        notifyProgress(() -> new Progress(100, "Task: " + taskDisplayString + " Finished Executing"),
                                                       sink, options, log);
                                        sink.complete();
                                    })
                                    // the error consumer is a no-op because doOnError above already forwarded to the sink
                                    .subscribe(result -> { }, throwable -> { });
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

                        notifyStepCompleted(new StepCompletion(storeType,
                                                               storeType != StoreType.NONE ? resultName : null,
                                                               storeType != StoreType.NONE ? lastStoredValue.get() : null), sink);
                        notifyProgress(() -> new Progress(100, "Task: " + taskDisplayString + " Finished Executing"), sink, options, log);
                        sink.complete();

                    // the error consumer is a no-op because doOnError above already forwarded to the sink
                    }).subscribe(value -> { }, throwable -> { });

                sink.onCancel(disposable);

            } else {
                storeIfDesired(context, options, sink, result);

                sink.next(new DefaultResult<>(new StepInfo(sequence), ResultType.VALUE, result));

                notifyStepCompleted(new StepCompletion(storeType,
                                                       storeType != StoreType.NONE ? resultName : null,
                                                       storeType != StoreType.NONE ? result : null), sink);

                notifyProgress(() -> new Progress(100, "Task: " + taskDisplayString + " Finished Executing"), sink, options, log);

                sink.complete();
            }
        }else{
            if(storeType != StoreType.NONE) {
                notifyDiagnostic(DiagnosticLevel.WARN,
                                 () -> "Task: " + taskDisplayString + " Result was requested to be stored, but result is NULL",
                                 sink,
                                 options,
                                 log);
            }

            sink.next(new DefaultResult<>(new StepInfo(sequence), ResultType.VALUE, null));

            notifyStepCompleted(new StepCompletion(storeType,
                                                   storeType != StoreType.NONE ? resultName : null,
                                                   null), sink);

            notifyProgress(() -> new Progress(100, "Task: " + taskDisplayString + " Finished Executing"), sink, options, log);

            sink.complete();
        }
    }

    private void storeIfDesired(JobContext context,
                                ResultOptions options,
                                FluxSink<Result<?>> sink,
                                Object result){
        if(storeType != StoreType.NONE) {

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
