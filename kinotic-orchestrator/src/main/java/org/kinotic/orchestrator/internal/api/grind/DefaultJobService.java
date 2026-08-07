

package org.kinotic.orchestrator.internal.api.grind;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.grind.ExecutionStatus;
import org.kinotic.domain.api.model.grind.StoreType;
import org.kinotic.domain.api.model.grind.TaskRecord;
import org.kinotic.domain.api.services.JobRunService;
import org.kinotic.domain.api.services.TaskRecordService;
import org.kinotic.orchestrator.api.grind.DiagnosticLevel;
import org.kinotic.orchestrator.api.grind.JobDefinition;
import org.kinotic.orchestrator.api.grind.JobExecution;
import org.kinotic.orchestrator.api.grind.JobService;
import org.kinotic.orchestrator.api.grind.ReplayEntry;
import org.kinotic.orchestrator.api.grind.ReplayLedger;
import org.kinotic.orchestrator.api.grind.Result;
import org.kinotic.orchestrator.api.grind.ResultOptions;
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
import java.util.concurrent.CompletableFuture;

/**
 *
 * Created by Navid Mitchell on 3/19/20
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultJobService implements JobService, ApplicationContextAware {

    private static final int RECORD_PAGE_SIZE = 500;

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

        return assembleInternal(jobDefinition, options, null);
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

        return executeRecorded(jobDefinition, options, null, assembleInternal(jobDefinition, options, null));
    }

    @Override
    public JobExecution resume(String jobRunId, JobDefinition jobDefinition) {
        return resume(jobRunId, jobDefinition, new ResultOptions(DiagnosticLevel.NONE, false));
    }

    @Override
    public JobExecution resume(String jobRunId, JobDefinition jobDefinition, ResultOptions options) {
        Validate.notBlank(jobRunId, "jobRunId Must not be blank");
        Validate.notNull(jobDefinition, "JobDefinition Must not be null");
        Validate.notBlank(jobDefinition.getName(), "JobDefinition name must be set to resume");
        Validate.notNull(options, "Options Must not be null");

        Flux<Result<?>> results =
            Flux.defer(() -> Mono.fromFuture(() -> jobRunService.findById(jobRunId))
                                 .switchIfEmpty(Mono.error(() -> new IllegalArgumentException("No JobRun found with id " + jobRunId)))
                                 .flatMapMany(originalRun -> {
                                     Flux<Result<?>> ret;
                                     if(!jobDefinition.getName().equals(originalRun.getName())){
                                         ret = Flux.error(new IllegalArgumentException("JobDefinition name " + jobDefinition.getName()
                                                 + " does not match the name " + originalRun.getName() + " recorded for run " + jobRunId));
                                     }else if(originalRun.getVersion() != null && !originalRun.getVersion().equals(jobDefinition.getVersion())){
                                         ret = Flux.error(new IllegalArgumentException("JobDefinition version " + jobDefinition.getVersion()
                                                 + " does not match the version " + originalRun.getVersion() + " recorded for run " + jobRunId));
                                     }else if(originalRun.getStatus() != ExecutionStatus.FAILED
                                             && originalRun.getStatus() != ExecutionStatus.CANCELLED){
                                         ret = Flux.error(new IllegalStateException("Run " + jobRunId + " is " + originalRun.getStatus()
                                                 + ", only FAILED or CANCELLED runs can be resumed"));
                                     }else{
                                         ret = Mono.fromFuture(() -> loadReplayLedger(jobRunId))
                                                   .flatMapMany(ledger -> assembleInternal(jobDefinition, options, ledger));
                                     }
                                     return ret;
                                 }));

        return executeRecorded(jobDefinition, options, jobRunId, results);
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

    private JobExecution executeRecorded(JobDefinition jobDefinition,
                                         ResultOptions options,
                                         String resumedFrom,
                                         Flux<Result<?>> results) {
        String jobRunId = UUID.randomUUID().toString();

        JobRunRecorder recorder = new JobRunRecorder(jobRunId,
                                                     jobDefinition,
                                                     resumedFrom,
                                                     jobRunService,
                                                     taskRecordService,
                                                     objectMapper);

        Flux<Result<?>> recorded = results.doOnSubscribe(subscription -> recorder.runStarted())
                                          .doOnNext(recorder::record)
                                          .doOnError(recorder::runFailed)
                                          .doOnCancel(recorder::runCancelled)
                                          .doOnComplete(recorder::runCompleted);

        // JobExecution multicasts, so these hooks see one subscription no matter how many subscribers attach
        return new JobExecution(jobRunId, recorded);
    }

    /**
     * Loads every COMPLETED record of the given run into a {@link ReplayLedger}, deserializing
     * STATE values. A value that cannot be restored leaves its entry without a value, so the
     * step re-executes rather than replaying corrupt state.
     */
    private CompletableFuture<ReplayLedger> loadReplayLedger(String jobRunId) {
        Map<String, ReplayEntry> entries = new HashMap<>();
        return loadRecordsPage(jobRunId, 0, entries).thenApply(v -> entries::get);
    }

    private CompletableFuture<Void> loadRecordsPage(String jobRunId, int page, Map<String, ReplayEntry> entries) {
        return taskRecordService.findAllForJobRun(jobRunId, Pageable.create(page, RECORD_PAGE_SIZE, null))
                                .thenCompose(recordPage -> {
                                    for(TaskRecord record : recordPage.getContent()){
                                        if(record.getStatus() == ExecutionStatus.COMPLETED){
                                            entries.put(record.getStepPath(), toReplayEntry(record));
                                        }
                                    }
                                    CompletableFuture<Void> ret;
                                    if(recordPage.getContent().size() < RECORD_PAGE_SIZE){
                                        ret = CompletableFuture.completedFuture(null);
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
