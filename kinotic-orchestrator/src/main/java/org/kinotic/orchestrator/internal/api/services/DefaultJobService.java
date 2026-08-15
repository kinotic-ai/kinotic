

package org.kinotic.orchestrator.internal.api.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.Kinotic;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.orchestrator.api.model.grind.ExecutionStatus;
import org.kinotic.orchestrator.api.model.grind.JobProgressEvent;
import org.kinotic.orchestrator.api.model.grind.JobProgressEventType;
import org.kinotic.orchestrator.api.model.grind.Progress;
import org.kinotic.orchestrator.api.model.grind.StoreType;
import org.kinotic.orchestrator.api.model.grind.TaskRecord;
import org.kinotic.orchestrator.api.services.JobRunService;
import org.kinotic.orchestrator.api.services.TaskRecordService;
import org.kinotic.orchestrator.api.model.grind.DiagnosticLevel;
import org.kinotic.orchestrator.api.model.grind.JobDefinition;
import org.kinotic.orchestrator.api.model.grind.JobExecution;
import org.kinotic.orchestrator.api.model.grind.JobOwner;
import org.kinotic.orchestrator.api.services.JobService;
import org.kinotic.orchestrator.api.model.grind.Result;
import org.kinotic.orchestrator.api.model.grind.ResultOptions;
import org.kinotic.orchestrator.internal.api.model.grind.DefaultJobContext;
import org.kinotic.orchestrator.internal.api.model.grind.JobDefinitionStep;
import org.kinotic.orchestrator.internal.api.model.grind.JobRunRecorder;
import org.kinotic.orchestrator.internal.api.model.grind.ReplayEntry;
import org.kinotic.orchestrator.internal.api.model.grind.ReplayLedger;
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

    private final JobRunService jobRunService;
    private final TaskRecordService taskRecordService;
    private final ObjectMapper objectMapper;
    private final Kinotic kinotic;

    /**
     * The runs currently executing on this node, keyed by run id, which {@link #watch(String)}
     * attaches observers to. A run is entered when it starts and removed when it terminates.
     */
    private final Map<String, JobExecution> executingRuns = new ConcurrentHashMap<>();

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
        return execute(jobDefinition, null, recordedDefaults());
    }

    @Override
    public JobExecution execute(JobDefinition jobDefinition, ResultOptions options) {
        return execute(jobDefinition, null, options);
    }

    @Override
    public JobExecution execute(JobDefinition jobDefinition, JobOwner owner) {
        return execute(jobDefinition, owner, recordedDefaults());
    }

    @Override
    public JobExecution execute(JobDefinition jobDefinition, JobOwner owner, ResultOptions options) {
        Validate.notNull(jobDefinition, "JobDefinition Must not be null");
        Validate.notBlank(jobDefinition.getName(), "JobDefinition name must be set to execute");
        Validate.notNull(options, "Options Must not be null");

        JobRunRecorder recorder = new JobRunRecorder(UUID.randomUUID().toString(),
                                                     kinotic.serverInfo().getNodeId(),
                                                     jobDefinition,
                                                     owner,
                                                     null,
                                                     jobRunService,
                                                     taskRecordService,
                                                     objectMapper);

        return executeRecorded(recorder, assembleInternal(jobDefinition, options, null));
    }

    @Override
    public JobExecution resume(String jobRunId, JobDefinition jobDefinition) {
        return resume(jobRunId, jobDefinition, recordedDefaults());
    }

    @Override
    public JobExecution resume(String jobRunId, JobDefinition jobDefinition, ResultOptions options) {
        Validate.notBlank(jobRunId, "jobRunId Must not be blank");
        Validate.notNull(jobDefinition, "JobDefinition Must not be null");
        Validate.notBlank(jobDefinition.getName(), "JobDefinition name must be set to resume");
        Validate.notNull(options, "Options Must not be null");

        JobRunRecorder recorder = new JobRunRecorder(UUID.randomUUID().toString(),
                                                     kinotic.serverInfo().getNodeId(),
                                                     jobDefinition,
                                                     null,
                                                     jobRunId,
                                                     jobRunService,
                                                     taskRecordService,
                                                     objectMapper);

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
                                         // the resumed run belongs to whoever owned the original
                                         recorder.ownerResolved(originalRun.getOrganizationId(),
                                                                originalRun.getApplicationId(),
                                                                originalRun.getProjectId());
                                         ret = Mono.fromFuture(() -> loadReplayLedger(jobRunId))
                                                   .flatMapMany(ledger -> assembleInternal(jobDefinition, options, ledger));
                                     }
                                     return ret;
                                 }));

        return executeRecorded(recorder, results);
    }

    @Override
    public Flux<JobProgressEvent> watch(String jobRunId) {
        Validate.notBlank(jobRunId, "jobRunId Must not be blank");
        return Flux.defer(() -> {
            JobExecution execution = executingRuns.get(jobRunId);
            Flux<JobProgressEvent> ret;
            if(execution == null){
                ret = Flux.empty();
            }else{
                // An observer follows the run, it does not own its outcome: a failure ends the
                // watch normally and is read from the run's terminal status and error instead
                ret = execution.getResults()
                               .mapNotNull(DefaultJobService::toProgressEvent)
                               .onErrorResume(throwable -> Flux.empty());
            }
            return ret;
        });
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

    private JobExecution executeRecorded(JobRunRecorder recorder, Flux<Result<?>> results) {
        String jobRunId = recorder.getJobRunId();
        // The registration hooks need the JobExecution assembled from this very Flux. Nothing
        // subscribes while JobExecution is being constructed, so the holder is always filled
        // before the hooks can read it.
        AtomicReference<JobExecution> registration = new AtomicReference<>();

        Flux<Result<?>> recorded = results.doOnSubscribe(subscription -> {
                                              // Entered only once the run starts, so an observer
                                              // can never be the subscriber that starts it
                                              executingRuns.put(jobRunId, registration.get());
                                              recorder.runStarted();
                                          })
                                          .doOnNext(recorder::record)
                                          .doOnError(recorder::runFailed)
                                          .doOnCancel(recorder::runCancelled)
                                          .doOnComplete(recorder::runCompleted)
                                          .doFinally(signalType -> executingRuns.remove(jobRunId));

        // JobExecution multicasts, so these hooks see one subscription no matter how many subscribers attach
        JobExecution ret = new JobExecution(jobRunId, recorded);
        registration.set(ret);
        return ret;
    }

    /**
     * Translates a {@link Result} into the event a watcher receives, or null for the result types
     * that carry nothing a watcher can use: VALUE and NOOP hold the job's own domain objects, and
     * DIAGNOSTIC and EXCEPTION repeat what the step lifecycle events already report.
     */
    private static JobProgressEvent toProgressEvent(Result<?> result) {
        String stepPath = result.getStepInfo().path();
        return switch(result.getResultType()){
            case STEP_STARTED -> new JobProgressEvent().setType(JobProgressEventType.STEP_STARTED)
                                                       .setStepPath(stepPath)
                                                       .setDescription((String) result.getValue());
            case STEP_COMPLETED -> new JobProgressEvent().setType(JobProgressEventType.STEP_COMPLETED)
                                                         .setStepPath(stepPath);
            case STEP_FAILED -> new JobProgressEvent().setType(JobProgressEventType.STEP_FAILED)
                                                      .setStepPath(stepPath)
                                                      .setMessage(String.valueOf(result.getValue()));
            case PROGRESS -> {
                Progress progress = (Progress) result.getValue();
                yield new JobProgressEvent().setType(JobProgressEventType.STEP_PROGRESS)
                                            .setStepPath(stepPath)
                                            .setPercentageComplete(progress.getPercentageComplete())
                                            .setMessage(progress.getMessage());
            }
            default -> null;
        };
    }

    /**
     * The options a recorded run uses when the caller names none. Progress results are on because
     * {@link #watch(String)} exists to deliver them, and they are never written to a record.
     */
    private static ResultOptions recordedDefaults() {
        return new ResultOptions(DiagnosticLevel.NONE, true);
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
