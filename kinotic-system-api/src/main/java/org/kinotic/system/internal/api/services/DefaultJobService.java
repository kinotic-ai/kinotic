

package org.kinotic.system.internal.api.services;

import io.vertx.core.Future;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.management.api.model.grind.RunStatus;
import org.kinotic.management.api.model.grind.StoreType;
import org.kinotic.management.api.model.grind.TaskRecord;
import org.kinotic.management.api.services.JobRecordService;
import org.kinotic.management.api.model.grind.DiagnosticLevel;
import org.kinotic.system.api.model.grind.JobDefinition;
import org.kinotic.system.api.model.grind.JobRunHandle;
import org.kinotic.management.api.model.grind.JobOwner;
import org.kinotic.system.api.services.JobService;
import org.kinotic.management.api.model.grind.Result;
import org.kinotic.system.api.model.grind.ResultOptions;
import org.kinotic.management.api.model.grind.ResultType;
import org.kinotic.management.api.model.grind.StepCompletion;
import org.kinotic.system.internal.api.model.grind.DefaultJobContext;
import org.kinotic.system.internal.api.model.grind.DefaultResult;
import org.kinotic.system.internal.api.model.grind.JobDefinitionStep;
import org.kinotic.system.internal.api.model.grind.JobRunRecorder;
import org.kinotic.system.internal.api.model.grind.ReplayEntry;
import org.kinotic.system.internal.api.model.grind.ReplayLedger;
import org.kinotic.system.internal.api.model.grind.Step;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 *
 * Created by Navid Mitchell on 3/19/20
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultJobService implements JobService, ApplicationContextAware {

    private static final int RECORD_PAGE_SIZE = 500;

    private final JobRecordService jobRecordService;
    private final ObjectMapper objectMapper;

    private final Map<String, JobRunHandle> activeExecutions = new ConcurrentHashMap<>();

    private ConfigurableApplicationContext applicationContext;


    @Override
    public Flux<Result<?>> assemble(JobDefinition jobDefinition) {
        return assemble(jobDefinition, new ResultOptions(DiagnosticLevel.NONE, false));
    }

    @Override
    public Flux<Result<?>> assemble(JobDefinition jobDefinition, ResultOptions options) {
        Validate.notNull(jobDefinition, "JobDefinition Must not be null");
        Validate.notNull(options, "Options Must not be null");

        return assembleInternal(jobDefinition, options, null);
    }

    @Override
    public JobRunHandle execute(JobDefinition jobDefinition) {
        return execute(jobDefinition, null, new ResultOptions(DiagnosticLevel.NONE, false));
    }

    @Override
    public JobRunHandle execute(JobDefinition jobDefinition, ResultOptions options) {
        return execute(jobDefinition, null, options);
    }

    @Override
    public JobRunHandle execute(JobDefinition jobDefinition, JobOwner owner) {
        return execute(jobDefinition, owner, new ResultOptions(DiagnosticLevel.NONE, false));
    }

    @Override
    public JobRunHandle execute(JobDefinition jobDefinition, JobOwner owner, ResultOptions options) {
        Validate.notNull(jobDefinition, "JobDefinition Must not be null");
        Validate.notBlank(jobDefinition.getName(), "JobDefinition name must be set to execute");
        Validate.notNull(options, "Options Must not be null");

        JobRunRecorder recorder = new JobRunRecorder(UUID.randomUUID().toString(),
                                                     jobDefinition,
                                                     owner,
                                                     null,
                                                     jobRecordService,
                                                     objectMapper);

        return executeRecorded(recorder, assembleInternal(jobDefinition, options, null));
    }

    @Override
    public JobRunHandle resume(String jobRunId, JobDefinition jobDefinition) {
        return resume(jobRunId, jobDefinition, new ResultOptions(DiagnosticLevel.NONE, false));
    }

    @Override
    public JobRunHandle resume(String jobRunId, JobDefinition jobDefinition, ResultOptions options) {
        Validate.notBlank(jobRunId, "jobRunId Must not be blank");
        Validate.notNull(jobDefinition, "JobDefinition Must not be null");
        Validate.notBlank(jobDefinition.getName(), "JobDefinition name must be set to resume");
        Validate.notNull(options, "Options Must not be null");

        JobRunRecorder recorder = new JobRunRecorder(UUID.randomUUID().toString(),
                                                     jobDefinition,
                                                     null,
                                                     jobRunId,
                                                     jobRecordService,
                                                     objectMapper);

        Flux<Result<?>> results =
            Flux.defer(() -> Mono.fromCompletionStage(() -> jobRecordService.findJobRunById(jobRunId).toCompletionStage())
                                 .switchIfEmpty(Mono.error(() -> new IllegalArgumentException("No JobRun found with id " + jobRunId)))
                                 .flatMapMany(originalRun -> {
                                     Flux<Result<?>> ret;
                                     if(!jobDefinition.getName().equals(originalRun.getName())){
                                         ret = Flux.error(new IllegalArgumentException("JobDefinition name " + jobDefinition.getName()
                                                 + " does not match the name " + originalRun.getName() + " recorded for run " + jobRunId));
                                     }else if(originalRun.getVersion() != null && !originalRun.getVersion().equals(jobDefinition.getVersion())){
                                         ret = Flux.error(new IllegalArgumentException("JobDefinition version " + jobDefinition.getVersion()
                                                 + " does not match the version " + originalRun.getVersion() + " recorded for run " + jobRunId));
                                     }else if(originalRun.getStatus() != RunStatus.FAILED
                                             && originalRun.getStatus() != RunStatus.CANCELLED){
                                         ret = Flux.error(new IllegalStateException("Run " + jobRunId + " is " + originalRun.getStatus()
                                                 + ", only FAILED or CANCELLED runs can be resumed"));
                                     }else{
                                         // the resumed run belongs to whoever owned the original
                                         recorder.ownerResolved(originalRun.getOrganizationId(),
                                                                originalRun.getApplicationId(),
                                                                originalRun.getProjectId());
                                         ret = Mono.fromCompletionStage(() -> loadReplayLedger(jobRunId).toCompletionStage())
                                                   .flatMapMany(ledger -> assembleInternal(jobDefinition, options, ledger));
                                     }
                                     return ret;
                                 }));

        return executeRecorded(recorder, results);
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = (ConfigurableApplicationContext) applicationContext;
    }

    private Flux<Result<?>> assembleInternal(JobDefinition jobDefinition, ResultOptions options, ReplayLedger replayLedger) {
        return Flux.defer(() -> {

            DefaultJobContext rootContext = new DefaultJobContext(applicationContext);

            JobDefinitionStep jobDefinitionStep = new JobDefinitionStep(0, jobDefinition);

            return Flux.from(jobDefinitionStep.assemble(String.valueOf(jobDefinitionStep.getSequence()), rootContext, options, replayLedger))
                       .doFinally(signalType -> rootContext.destroy());
        });
    }

    private JobRunHandle executeRecorded(JobRunRecorder recorder, Flux<Result<?>> results) {
        String jobRunId = recorder.getJobRunId();
        AtomicReference<JobRunHandle> executionRef = new AtomicReference<>();
        Flux<Result<?>> recorded = results.doOnSubscribe(subscription -> {
                                              // Registered on first subscription rather than at creation, so a
                                              // watcher can only attach to a run that has started and can never
                                              // be the subscription that triggers execution
                                              activeExecutions.put(jobRunId, executionRef.get());
                                              recorder.runStarted();
                                          })
                                          .doOnNext(recorder::record)
                                          .doOnError(recorder::runFailed)
                                          .doOnCancel(recorder::runCancelled)
                                          .doOnComplete(recorder::runCompleted)
                                          .doFinally(signalType -> activeExecutions.remove(jobRunId));

        // JobRunHandle multicasts, so these hooks see one subscription no matter how many subscribers attach
        JobRunHandle execution = new JobRunHandle(jobRunId, recorded);
        executionRef.set(execution);
        return execution;
    }

    @Override
    public Flux<Result<?>> watchExecution(String jobRunId) {
        Validate.notBlank(jobRunId, "jobRunId Must not be blank");
        JobRunHandle execution = activeExecutions.get(jobRunId);
        Flux<Result<?>> ret;
        if(execution == null){
            ret = Flux.empty();
        }else{
            ret = execution.getResults().map(result -> toWireSafeResult(jobRunId, result));
        }
        return ret;
    }

    /**
     * Rebuilds results whose value cannot cross a serialization boundary: dynamic steps are
     * internal {@link Step} graphs holding live {@link org.kinotic.system.api.model.grind.Task}
     * instances, failures are {@link Throwable}s, and completions and values carry arbitrary
     * user objects. Everything else passes through untouched.
     */
    private Result<?> toWireSafeResult(String jobRunId, Result<?> result) {
        Result<?> ret;
        switch(result.getResultType()){
            case DYNAMIC_STEPS -> ret = new DefaultResult<>(result.getStepInfo(),
                                                            ResultType.DYNAMIC_STEPS,
                                                            JobRunRecorder.discoveredStepRecords(jobRunId,
                                                                                                 result.getStepInfo().path(),
                                                                                                 (Step) result.getValue()));
            case STEP_FAILED -> ret = new DefaultResult<>(result.getStepInfo(),
                                                          ResultType.STEP_FAILED,
                                                          result.getValue().toString());
            case STEP_COMPLETED -> {
                StepCompletion completion = (StepCompletion) result.getValue();
                ret = new DefaultResult<>(result.getStepInfo(),
                                          ResultType.STEP_COMPLETED,
                                          new StepCompletion(completion.getStoreType(), completion.getStoredName(), null));
            }
            case VALUE -> ret = new DefaultResult<>(result.getStepInfo(), ResultType.VALUE, null);
            default -> ret = result;
        }
        return ret;
    }

    /**
     * Loads every COMPLETED record of the given run into a {@link ReplayLedger}, deserializing
     * STATE values. A value that cannot be restored leaves its entry without a value, so the
     * step re-executes rather than replaying corrupt state.
     */
    private Future<ReplayLedger> loadReplayLedger(String jobRunId) {
        Map<String, ReplayEntry> entries = new HashMap<>();
        return loadRecordsPage(jobRunId, 0, entries).map(v -> entries::get);
    }

    private Future<Void> loadRecordsPage(String jobRunId, int page, Map<String, ReplayEntry> entries) {
        return jobRecordService.findTaskRecordsForJobRun(jobRunId, Pageable.create(page, RECORD_PAGE_SIZE, null))
                                .compose(recordPage -> {
                                    for(TaskRecord record : recordPage.getContent()){
                                        if(record.getStatus() == RunStatus.COMPLETED){
                                            entries.put(record.getStepPath(), toReplayEntry(record));
                                        }
                                    }
                                    Future<Void> ret;
                                    if(recordPage.getContent().size() < RECORD_PAGE_SIZE){
                                        ret = Future.succeededFuture();
                                    }else{
                                        ret = loadRecordsPage(jobRunId, page + 1, entries);
                                    }
                                    return ret;
                                });
    }

    private ReplayEntry toReplayEntry(TaskRecord record) {
        Object value = null;
        if(record.getStoreType() == StoreType.STATE && record.getResultValue() != null && record.getResultValueType() != null){
            try {
                Class<?> type = Class.forName(record.getResultValueType());
                value = objectMapper.treeToValue(record.getResultValue(), type);
            } catch (Exception e) {
                log.warn("Could not restore stored state for step {} of run {}, the step will re-execute",
                         record.getStepPath(), record.getJobRunId(), e);
            }
        }
        StoreType storeType = record.getStoreType() != null ? record.getStoreType() : StoreType.NONE;
        return new ReplayEntry(storeType, record.isDynamicSteps(), value);
    }

}
