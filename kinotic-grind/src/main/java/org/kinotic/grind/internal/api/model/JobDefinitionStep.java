

package org.kinotic.grind.internal.api.model;

import org.kinotic.grind.api.model.Result;
import org.kinotic.grind.api.model.ResultType;
import org.kinotic.grind.api.model.StepInfo;
import org.kinotic.grind.api.model.StepCompletion;
import org.kinotic.grind.api.model.Progress;
import org.kinotic.grind.api.model.DiagnosticLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.kinotic.grind.api.model.StoreType;
import org.kinotic.grind.api.model.*;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;

/**
 * Provides functionality for a {@link Step} that will execute a {@link JobDefinition}
 *
 *
 * Created by Navid Mitchell on 8/5/20
 */
public class JobDefinitionStep extends AbstractStep {

    private static final Logger log = LoggerFactory.getLogger(JobDefinitionStep.class);

    private final DefaultJobDefinition jobDefinition;
    private final String taskDisplayString;

    public JobDefinitionStep(int sequence, JobDefinition jobDefinition) {
        super(sequence);
        // every JobDefinition is created by JobDefinition.create, so the engine can rely on
        // the concrete type for step access without exposing steps on the api interface
        this.jobDefinition = (DefaultJobDefinition) jobDefinition;
        this.taskDisplayString = "\"" + jobDefinition.getDescription() + "\"";
    }

    @Override
    public String getDescription() {
        return jobDefinition.getDescription();
    }

    /**
     * The static child steps of the definition this step executes.
     */
    List<Step> getSteps() {
        return jobDefinition.getSteps();
    }

    @Override
    public Publisher<Result<?>> assemble(String stepPath, JobContext context, ResultOptions options, ReplayLedger replayLedger) {
        return Flux.create(sink -> {
            try {

                notifyStepStarted(jobDefinition.getDescription(), sink);
                notifyProgress(() -> new Progress(0,
                                                  "JobDefinition: " + taskDisplayString + " Scope: " + jobDefinition.getScope() + " Executing"), sink, options, log);

                boolean destroyContextOnFinally = jobDefinition.getScope() == JobScope.CHILD;
                JobContext contextToUse = destroyContextOnFinally ? context.createChild() : context;

                notifyDiagnostic(DiagnosticLevel.TRACE,
                                 () -> "JobDefinition: " +taskDisplayString + " Assembling Steps",
                                 sink, options, log);

                List<Publisher<Result<?>>> assembledTaskDefinitions = new ArrayList<>();

                for(Step step: jobDefinition.getSteps()){
                    assembledTaskDefinitions.add(step.assemble(stepPath + "/" + step.getSequence(), contextToUse, options, replayLedger));
                }

                Flux<Result<?>> jobFlux;
                if(jobDefinition.isParallel()){
                    // boundedElastic since tasks are allowed to block, which would starve the shared parallel scheduler
                    jobFlux = Flux.merge(assembledTaskDefinitions)
                              .parallel()
                              .runOn(Schedulers.boundedElastic())
                              .sequential();
                }else{
                    jobFlux = Flux.concat(assembledTaskDefinitions);
                }

                int percentPerStep = !assembledTaskDefinitions.isEmpty() ? (int) Math.floor(100F / assembledTaskDefinitions.size()) : 100;
                ProgressHolder progressHolder = new ProgressHolder();
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
                                                   notifyStepFailed(throwable, sink);
                                                   notifyException(() -> "JobDefinition: " + taskDisplayString + " Exception during execution ", throwable, sink, options, log);
                                                   sink.error(throwable);
                                               })
                                               .doOnComplete(() -> {
                                                   notifyStepCompleted(new StepCompletion(StoreType.NONE, null, null), sink);
                                                   notifyProgress(() -> new Progress(100,
                                                                                     "JobDefinition: " + taskDisplayString + " Finished Executing"), sink, options, log);
                                                   sink.complete();
                                               })
                                               .doFinally(signalType -> {
                                                   if(destroyContextOnFinally) {
                                                       notifyDiagnostic(DiagnosticLevel.TRACE,
                                                                        () -> "JobDefinition: "+taskDisplayString+" Destroying Job Execution Scope",
                                                                        sink, options, log);

                                                       contextToUse.destroy();
                                                   }
                                               })
                                               // the error consumer is a no-op because doOnError above already forwarded to the sink
                                               .subscribe(result -> { }, throwable -> { });
                sink.onCancel(disposable);

            } catch (Exception throwable) {
                notifyStepFailed(throwable, sink);
                notifyException(() -> "JobDefinition: " + taskDisplayString + " Exception during execution ", throwable, sink, options, log);
                sink.error(throwable);
            }
        });
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
