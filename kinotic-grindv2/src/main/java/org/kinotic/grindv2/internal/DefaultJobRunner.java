package org.kinotic.grindv2.internal;

import org.apache.commons.lang3.Validate;
import org.kinotic.grindv2.api.ExecutionStatus;
import org.kinotic.grindv2.api.JobDefinition;
import org.kinotic.grindv2.api.JobOwner;
import org.kinotic.grindv2.api.JobRun;
import org.kinotic.grindv2.api.JobRunEvent;
import org.kinotic.grindv2.api.JobRunHandle;
import org.kinotic.grindv2.api.JobRunRepository;
import org.kinotic.grindv2.api.JobRunner;
import org.kinotic.grindv2.api.StepRecord;
import org.kinotic.grindv2.api.StoreType;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ConfigurableApplicationContext;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import io.vertx.core.Future;

import tools.jackson.databind.ObjectMapper;

/**
 * Default {@link JobRunner}: each run executes on its own virtual thread, recorded through
 * the {@link JobRunRepository} and observable through the handle's event stream. Execution
 * begins when the handle's events are first subscribed.
 */
public class DefaultJobRunner implements JobRunner, ApplicationContextAware {

    private final JobRunRepository repository;
    private final StateSerializer stateSerializer;

    private ConfigurableApplicationContext applicationContext;

    public DefaultJobRunner(JobRunRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.stateSerializer = new StateSerializer(objectMapper);
    }

    @Override
    public JobRunHandle run(JobDefinition jobDefinition, JobOwner owner) {
        Validate.notNull(jobDefinition, "jobDefinition cannot be null");
        Validate.notNull(owner, "owner cannot be null");
        Validate.notBlank(jobDefinition.getName(), "JobDefinition name must be set to run");

        String runId = UUID.randomUUID().toString();
        DefaultJobDefinition definition = (DefaultJobDefinition) jobDefinition;
        RunRecorder recorder = new RunRecorder(runId, definition, owner, null, repository);

        Flux<JobRunEvent> upstream = Flux.create(sink ->
                startRunThread(sink, runId, () -> interpreter(sink, runId, definition, recorder, Map.of()).run()));
        return new JobRunHandle(runId, upstream);
    }

    @Override
    public JobRunHandle resume(String jobRunId, JobDefinition jobDefinition) {
        Validate.notBlank(jobRunId, "jobRunId cannot be blank");
        Validate.notNull(jobDefinition, "jobDefinition cannot be null");
        Validate.notBlank(jobDefinition.getName(), "JobDefinition name must be set to resume");

        String runId = UUID.randomUUID().toString();
        DefaultJobDefinition definition = (DefaultJobDefinition) jobDefinition;
        RunRecorder recorder = new RunRecorder(runId, definition, null, jobRunId, repository);

        Flux<JobRunEvent> upstream = Flux.create(sink ->
                startRunThread(sink, runId, () -> {
                    EventStreamAdapter adapter = new EventStreamAdapter(sink);
                    try {
                        Map<String, ReplayEntry> replay = loadReplay(jobRunId, definition, recorder);
                        new JobInterpreter(applicationContext, runId, definition,
                                           List.of(recorder, adapter), replay, stateSerializer).run();
                    } catch (RunCancelledException e) {
                        recorder.runCancelled();
                        adapter.runCancelled();
                    } catch (Throwable t) {
                        // loading or validating the original run failed before execution began
                        recorder.runFailed(t);
                        adapter.runFailed(t);
                    }
                }));
        return new JobRunHandle(runId, upstream);
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = (ConfigurableApplicationContext) applicationContext;
    }

    private JobInterpreter interpreter(FluxSink<JobRunEvent> sink, String runId,
                                       DefaultJobDefinition definition, RunRecorder recorder,
                                       Map<String, ReplayEntry> replay) {
        return new JobInterpreter(applicationContext, runId, definition,
                                  List.of(recorder, new EventStreamAdapter(sink)), replay, stateSerializer);
    }

    private void startRunThread(FluxSink<JobRunEvent> sink, String runId, Runnable body) {
        Thread thread = Thread.ofVirtual().name("grindv2-run-" + runId).unstarted(body);
        sink.onCancel(thread::interrupt);
        thread.start();
    }

    /**
     * Loads the original run, validates it can be resumed by this definition, and builds the
     * replay entries for its COMPLETED steps.
     */
    private Map<String, ReplayEntry> loadReplay(String jobRunId, DefaultJobDefinition definition,
                                                RunRecorder recorder) {
        JobRun original = await(repository.findRun(jobRunId));
        if (original == null) {
            throw new IllegalArgumentException("No JobRun found with id " + jobRunId);
        }
        if (!definition.getName().equals(original.getName())) {
            throw new IllegalArgumentException("JobDefinition name " + definition.getName()
                    + " does not match the name " + original.getName() + " recorded for run " + jobRunId);
        }
        if (original.getVersion() != null && !original.getVersion().equals(definition.getVersion())) {
            throw new IllegalArgumentException("JobDefinition version " + definition.getVersion()
                    + " does not match the version " + original.getVersion() + " recorded for run " + jobRunId);
        }
        if (original.getStatus() != ExecutionStatus.FAILED && original.getStatus() != ExecutionStatus.CANCELLED) {
            throw new IllegalStateException("Run " + jobRunId + " is " + original.getStatus()
                    + ", only FAILED or CANCELLED runs can be resumed");
        }
        // the resumed run belongs to whoever owned the original
        recorder.ownerResolved(original.getOrganizationId(), original.getApplicationId(), original.getProjectId());

        Map<String, ReplayEntry> ret = new HashMap<>();
        for (StepRecord record : await(repository.findSteps(jobRunId))) {
            if (record.getStatus() == ExecutionStatus.COMPLETED) {
                Object value = null;
                if (record.getStoreType() == StoreType.STATE
                        && record.getResultValue() != null && record.getResultValueType() != null) {
                    value = stateSerializer.deserialize(
                            new SerializedState(record.getResultValueType(), record.getResultValue()));
                }
                StoreType storeType = record.getStoreType() != null ? record.getStoreType() : StoreType.NONE;
                ret.put(record.getStepPath(), new ReplayEntry(storeType, record.isDynamicSteps(), value));
            }
        }
        return ret;
    }

    private <T> T await(Future<T> future) {
        try {
            return future.toCompletionStage().toCompletableFuture().get();
        } catch (InterruptedException e) {
            throw new RunCancelledException();
        } catch (ExecutionException e) {
            throw new IllegalStateException("Could not load the run being resumed", e.getCause());
        }
    }

}
