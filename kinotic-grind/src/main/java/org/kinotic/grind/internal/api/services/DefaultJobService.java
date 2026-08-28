package org.kinotic.grind.internal.api.services;

import org.apache.commons.lang3.Validate;
import org.kinotic.grind.api.model.ExecutionStatus;
import org.kinotic.grind.api.model.JobDefinition;
import org.kinotic.grind.api.model.JobOwner;
import org.kinotic.grind.api.model.JobRun;
import org.kinotic.grind.api.model.events.JobRunEvent;
import org.kinotic.grind.api.model.JobRunHandle;
import org.kinotic.grind.api.model.StoreType;
import org.kinotic.grind.api.model.TaskRecord;
import org.kinotic.grind.internal.api.repositories.JobRunRepository;
import org.kinotic.grind.api.services.JobService;
import org.kinotic.grind.internal.api.model.DefaultJobDefinition;
import org.kinotic.grind.internal.model.ReplayEntry;
import org.kinotic.grind.internal.model.RunCancelledException;
import org.kinotic.grind.internal.model.SerializedState;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;

import io.vertx.core.Future;
import io.vertx.core.Vertx;

import tools.jackson.databind.ObjectMapper;

/**
 * Default {@link JobService}: each run executes on its own Vert.x virtual-thread context, so
 * tasks may block on their results while {@code Vertx.currentContext()} - and the platform
 * services that hang off it - resolve throughout the run. Runs are recorded through the
 * {@link JobRunRepository} and observable through the handle's event stream. Execution begins
 * when the handle's events are first subscribed.
 */
@Component
public class DefaultJobService implements JobService, ApplicationContextAware {

    private final JobRunRepository repository;
    private final StateSerializer stateSerializer;
    private final RunThreadFactory runThreads;

    private final Map<String, JobRunHandle> activeRuns = new ConcurrentHashMap<>();

    private ConfigurableApplicationContext applicationContext;

    public DefaultJobService(JobRunRepository repository, ObjectMapper objectMapper, Vertx vertx) {
        this.repository = repository;
        this.stateSerializer = new StateSerializer(objectMapper);
        this.runThreads = new RunThreadFactory(vertx);
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
        return registeredHandle(runId, upstream);
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
                    RunEventEmitter emitter = new RunEventEmitter(sink);
                    try {
                        Map<String, ReplayEntry> replay = loadReplay(jobRunId, definition, recorder);
                        new JobInterpreter(applicationContext, runId, definition,
                                           List.of(recorder, emitter), replay, stateSerializer, runThreads).run();
                    } catch (RunCancelledException e) {
                        recorder.runCancelled();
                        emitter.runCancelled();
                    } catch (Throwable t) {
                        // loading or validating the original run failed before execution began
                        recorder.runFailed(t);
                        emitter.runFailed(t);
                    }
                }));
        return registeredHandle(runId, upstream);
    }

    @Override
    public Flux<JobRunEvent> watchRun(String jobRunId) {
        Validate.notBlank(jobRunId, "jobRunId cannot be blank");
        JobRunHandle handle = activeRuns.get(jobRunId);
        Flux<JobRunEvent> ret;
        if (handle == null) {
            ret = Flux.empty();
        } else {
            ret = handle.getEvents();
        }
        return ret;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = (ConfigurableApplicationContext) applicationContext;
    }

    /**
     * Wraps the run's event stream in the handle a watcher can attach to, registering the run
     * as active for as long as it executes.
     */
    private JobRunHandle registeredHandle(String runId, Flux<JobRunEvent> upstream) {
        AtomicReference<JobRunHandle> handleRef = new AtomicReference<>();
        // JobRunHandle multicasts, so these hooks see one subscription no matter how many
        // subscribers attach. Registering on that subscription rather than at creation keeps a
        // watcher from ever being the subscription that triggers execution
        Flux<JobRunEvent> registered = upstream.doOnSubscribe(subscription -> activeRuns.put(runId, handleRef.get()))
                                               .doFinally(signalType -> activeRuns.remove(runId));
        JobRunHandle handle = new JobRunHandle(runId, registered);
        handleRef.set(handle);
        return handle;
    }

    private JobInterpreter interpreter(FluxSink<JobRunEvent> sink, String runId,
                                       DefaultJobDefinition definition, RunRecorder recorder,
                                       Map<String, ReplayEntry> replay) {
        return new JobInterpreter(applicationContext, runId, definition,
                                  List.of(recorder, new RunEventEmitter(sink)), replay, stateSerializer, runThreads);
    }

    private void startRunThread(FluxSink<JobRunEvent> sink, String runId, Runnable body) {
        RunThread runThread = runThreads.start("grind-run-" + runId, body);
        sink.onCancel(runThread::interrupt);
    }

    /**
     * Loads the original run, validates it can be resumed by this definition, and builds the
     * replay entries for its COMPLETED tasks.
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
        for (TaskRecord record : await(repository.findTasks(jobRunId))) {
            if (record.getStatus() == ExecutionStatus.COMPLETED) {
                Object value = null;
                if (record.getStoreType() == StoreType.STATE
                        && record.getResultValue() != null && record.getResultValueType() != null) {
                    value = stateSerializer.deserialize(
                            new SerializedState(record.getResultValueType(), record.getResultValue()));
                }
                StoreType storeType = record.getStoreType() != null ? record.getStoreType() : StoreType.NONE;
                ret.put(record.getTaskPath(), new ReplayEntry(storeType, record.isDynamicTasks(), value));
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
