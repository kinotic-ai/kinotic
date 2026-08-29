package org.kinotic.grind.internal.api.services;

import org.kinotic.grind.internal.api.model.DefaultJobContext;
import org.kinotic.grind.internal.api.model.DefaultJobDefinition;
import org.kinotic.grind.internal.model.DefinitionNode;
import org.kinotic.grind.internal.model.ReplayEntry;
import org.kinotic.grind.internal.model.RunCancelledException;
import org.kinotic.grind.internal.model.SerializedState;
import org.kinotic.grind.internal.model.JobNode;
import org.kinotic.grind.internal.model.TaskNode;
import lombok.SneakyThrows;
import org.kinotic.grind.api.model.ExecutionStatus;
import org.kinotic.grind.api.model.JobDefinition;
import org.kinotic.grind.api.model.JobScope;
import org.kinotic.grind.api.model.ProgressReporter;
import org.kinotic.grind.api.model.TaskRecord;
import org.kinotic.grind.api.model.Store;
import org.kinotic.grind.api.model.StoreType;
import org.kinotic.grind.api.model.Task;
import org.reactivestreams.Publisher;
import org.springframework.context.ConfigurableApplicationContext;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Executes one run of a {@link JobDefinition} by walking its task tree on the run's thread,
 * reporting every lifecycle transition to the {@link RunListener}s. Dynamic structure - a
 * task returning a {@link Task} or {@link JobDefinition} - is discovered, reported, and
 * descended into as it appears. Cancellation arrives as thread interruption and unwinds as
 * {@link RunCancelledException} without recording task failures.
 */
public class JobInterpreter {

    private final ConfigurableApplicationContext applicationContext;
    private final String runId;
    private final DefaultJobDefinition rootDefinition;
    private final List<RunListener> listeners;
    private final Map<String, ReplayEntry> replay;
    private final StateSerializer stateSerializer;
    private final RunThreadFactory runThreads;
    // Serializes listener dispatch so parallel definitions cannot interleave callbacks
    private final Object dispatchLock = new Object();
    // The task path executing on each thread, stamped around Task.execute so the scope's
    // ProgressReporter can attribute reports; parallel children stamp their own threads
    private final ThreadLocal<String> executingTaskPath = new ThreadLocal<>();

    public JobInterpreter(ConfigurableApplicationContext applicationContext,
                          String runId,
                          DefaultJobDefinition rootDefinition,
                          List<RunListener> listeners,
                          Map<String, ReplayEntry> replay,
                          StateSerializer stateSerializer,
                          RunThreadFactory runThreads) {
        this.applicationContext = applicationContext;
        this.runId = runId;
        this.rootDefinition = rootDefinition;
        this.listeners = listeners;
        this.replay = replay;
        this.stateSerializer = stateSerializer;
        this.runThreads = runThreads;
    }

    /**
     * Executes the run to its terminal state. Never throws: the outcome is reported through
     * the listeners.
     */
    public void run() {
        DefaultJobContext rootScope = new DefaultJobContext(applicationContext);
        try {
            notifyRunStarted();
            List<TaskRecord> staticTree = new ArrayList<>();
            collectRecords("", new DefinitionNode(0, rootDefinition), staticTree);
            notifyTasksDiscovered("0", staticTree, false);
            rootScope.storeBean("progressReporter", progressReporter());
            seedInputs(rootScope);
            executeDefinition("0", rootScope, rootDefinition);
            notifyRunCompleted();
        } catch (RunCancelledException e) {
            notifyRunCancelled();
        } catch (Throwable t) {
            notifyRunFailed(t);
        } finally {
            rootScope.destroy();
        }
    }

    private void seedInputs(DefaultJobContext rootScope) {
        for (Object input : rootDefinition.getInputs()) {
            ScopeWriter.store(rootScope, StoreType.RESULT, null, input);
        }
    }

    private void executeDefinition(String path, DefaultJobContext parentScope, DefaultJobDefinition definition) {
        notifyTaskStarted(path, definition.getDescription());
        boolean destroyOnExit = definition.getScope() == JobScope.CHILD;
        DefaultJobContext scope = destroyOnExit ? parentScope.createChild() : parentScope;
        try {
            if (definition.isParallel()) {
                executeChildrenParallel(path, scope, definition.getTasks());
            } else {
                for (JobNode child : definition.getTasks()) {
                    executeChild(path, scope, child);
                }
            }
            notifyTaskCompleted(path, Store.none(), null, null, null);
        } catch (Throwable t) {
            handleTaskFailure(path, t);
        } finally {
            if (destroyOnExit) {
                scope.destroy();
            }
        }
    }

    private void executeChild(String parentPath, DefaultJobContext scope, JobNode child) {
        if (Thread.currentThread().isInterrupted()) {
            throw new RunCancelledException();
        }
        String childPath = parentPath + "/" + child.sequence();
        if (child instanceof TaskNode taskNode) {
            executeTask(childPath, scope, taskNode);
        } else if (child instanceof DefinitionNode definitionNode) {
            executeDefinition(childPath, scope, definitionNode.definition());
        }
    }

    private void executeTask(String path, DefaultJobContext scope, TaskNode node) {
        ReplayEntry entry = replay.get(path);
        // A task that produced dynamic tasks must re-execute so they are regenerated;
        // the regenerated tasks then consult the replay entries at their own paths
        boolean completedInOriginal = entry != null && !entry.dynamicTasks();

        if (completedInOriginal && node.store().getType() == StoreType.NONE) {
            notifyTaskStarted(path, node.description());
            notifyTaskCompleted(path, node.store(), null, null, null);
            return;
        }
        if (completedInOriginal && node.store().getType() == StoreType.STATE && entry.value() != null) {
            notifyTaskStarted(path, node.description());
            String storedName = ScopeWriter.store(scope, node.store().getType(), node.store().getName(), entry.value());
            SerializedState state = stateSerializer.serialize(node.description(), entry.value());
            notifyTaskCompleted(path, node.store(), storedName, entry.value(), state);
            return;
        }

        // RESULT reloads from its source of truth: the declared reload task when there is one,
        // otherwise the task itself, which must then be safe to re-run
        Task<?> taskToRun = completedInOriginal && node.store().getReloadTask() != null
                ? node.store().getReloadTask() : node.task();
        notifyTaskStarted(path, node.description());
        try {
            Object raw = executeOnPath(path, taskToRun, scope);
            if (raw instanceof Task<?> dynamicTask) {
                // The dynamic task carries this task's store, so it stores the value
                TaskNode dynamicNode = new TaskNode(1, dynamicTask, node.store());
                discoverDynamic(path, dynamicNode);
                executeTask(path + "/1", scope, dynamicNode);
                notifyTaskCompleted(path, Store.none(), null, null, null);
            } else if (raw instanceof JobDefinition dynamicDefinition) {
                DefinitionNode dynamicNode = new DefinitionNode(1, (DefaultJobDefinition) dynamicDefinition);
                discoverDynamic(path, dynamicNode);
                executeDefinition(path + "/1", scope, dynamicNode.definition());
                notifyTaskCompleted(path, Store.none(), null, null, null);
            } else {
                Object value = awaitValue(raw);
                String storedName = ScopeWriter.store(scope, node.store().getType(), node.store().getName(), value);
                notifyTaskCompleted(path, node.store(), storedName, value, serializeIfNeeded(node, value));
            }
        } catch (Throwable t) {
            handleTaskFailure(path, t);
        }
    }

    private Object executeOnPath(String path, Task<?> task, DefaultJobContext scope) throws Exception {
        executingTaskPath.set(path);
        try {
            return task.execute(scope);
        } finally {
            executingTaskPath.remove();
        }
    }

    /**
     * The {@link ProgressReporter} every job scope carries: reports attach to the task
     * executing on the calling thread, and a report from a thread no task is executing on -
     * one the task spawned itself - is dropped.
     */
    private ProgressReporter progressReporter() {
        return (percentageComplete, message) -> {
            String path = executingTaskPath.get();
            if (path != null) {
                notifyTaskProgress(path, percentageComplete, message);
            }
        };
    }

    /**
     * Serializes the completed value when something needs its JSON form: durable state always,
     * under the strict {@link StoreType#STATE} contract, otherwise a wire-published value,
     * which only has to serialize since it is never restored.
     */
    private SerializedState serializeIfNeeded(TaskNode node, Object value) {
        SerializedState ret = null;
        if (node.store().getType() == StoreType.STATE) {
            ret = stateSerializer.serialize(node.description(), value);
        } else if (node.store().isWire()) {
            ret = stateSerializer.serializeWireValue(node.description(), value);
        }
        return ret;
    }

    private void executeChildrenParallel(String parentPath, DefaultJobContext scope, List<JobNode> children) {
        // each child gets its own virtual-thread context, so context-locals never cross siblings
        List<RunThread> threads = new CopyOnWriteArrayList<>();
        AtomicReference<Throwable> firstError = new AtomicReference<>();
        for (JobNode child : children) {
            threads.add(runThreads.start("grind-" + runId + "-" + parentPath + "/" + child.sequence(),
                                         () -> {
                                             try {
                                                 executeChild(parentPath, scope, child);
                                             } catch (Throwable t) {
                                                 // first failure wins and cancels the siblings; a sibling's
                                                 // own RunCancelledException from that interrupt is
                                                 // swallowed here. Interrupting the failed child itself is
                                                 // harmless - its body is already unwinding
                                                 if (firstError.compareAndSet(null, t)) {
                                                     threads.forEach(RunThread::interrupt);
                                                 }
                                             }
                                         }));
        }
        if (firstError.get() != null) {
            // an early failure may have run before later siblings were added to the list
            threads.forEach(RunThread::interrupt);
        }
        joinAll(threads);
        Throwable error = firstError.get();
        if (error != null) {
            rethrow(error);
        }
    }

    private void joinAll(List<RunThread> threads) {
        boolean cancelled = false;
        for (RunThread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                // the run itself was cancelled: stop the children, then keep joining so no
                // child outlives the run
                cancelled = true;
                threads.forEach(RunThread::interrupt);
                joinUninterruptibly(thread);
            }
        }
        if (cancelled) {
            throw new RunCancelledException();
        }
    }

    private void joinUninterruptibly(RunThread thread) {
        boolean joined = false;
        while (!joined) {
            try {
                thread.join();
                joined = true;
            } catch (InterruptedException e) {
                // already cancelling; keep waiting for the child to unwind
            }
        }
    }

    /**
     * Awaits an asynchronous task result: a {@code CompletionStage}, Vert.x {@code Future},
     * or {@code Publisher} (whose last emission is the value). Any other object is the value
     * itself.
     */
    private Object awaitValue(Object raw) {
        Object ret;
        if (raw instanceof CompletionStage<?> stage) {
            ret = awaitStage(stage);
        } else if (raw instanceof io.vertx.core.Future<?> future) {
            ret = awaitStage(future.toCompletionStage());
        } else if (raw instanceof Publisher<?> publisher) {
            ret = awaitPublisher(publisher);
        } else {
            ret = raw;
        }
        return ret;
    }

    @SneakyThrows
    private Object awaitStage(CompletionStage<?> stage) {
        try {
            return stage.toCompletableFuture().get();
        } catch (InterruptedException e) {
            throw new RunCancelledException();
        } catch (ExecutionException e) {
            throw e.getCause() != null ? e.getCause() : e;
        }
    }

    private Object awaitPublisher(Publisher<?> publisher) {
        try {
            return Flux.from(publisher).blockLast();
        } catch (RuntimeException e) {
            if (Exceptions.unwrap(e) instanceof InterruptedException) {
                throw new RunCancelledException();
            }
            throw e;
        }
    }

    /**
     * Reports the failure and rethrows it. Cancellation passes through unreported: the
     * recorder marks in-flight tasks CANCELLED when the run terminates.
     */
    @SneakyThrows
    private void handleTaskFailure(String path, Throwable error) {
        if (!(error instanceof RunCancelledException)) {
            notifyTaskFailed(path, error);
        }
        throw error;
    }

    @SneakyThrows
    private void rethrow(Throwable error) {
        throw error;
    }

    private void discoverDynamic(String producerPath, JobNode node) {
        List<TaskRecord> records = new ArrayList<>();
        collectRecords(producerPath, node, records);
        notifyTasksDiscovered(producerPath, records, true);
    }

    private void collectRecords(String parentPath, JobNode node, List<TaskRecord> collected) {
        String path = parentPath.isEmpty() ? String.valueOf(node.sequence())
                                           : parentPath + "/" + node.sequence();
        collected.add(new TaskRecord().setId(runId + ":" + path)
                                      .setJobRunId(runId)
                                      .setTaskPath(path)
                                      .setDescription(node.description())
                                      .setStatus(ExecutionStatus.PENDING));
        if (node instanceof DefinitionNode definitionNode) {
            for (JobNode child : definitionNode.definition().getTasks()) {
                collectRecords(path, child, collected);
            }
        }
    }

    private void notifyRunStarted() {
        synchronized (dispatchLock) {
            listeners.forEach(RunListener::runStarted);
        }
    }

    private void notifyTasksDiscovered(String parentPath, List<TaskRecord> discovered, boolean dynamic) {
        synchronized (dispatchLock) {
            listeners.forEach(listener -> listener.tasksDiscovered(parentPath, discovered, dynamic));
        }
    }

    private void notifyTaskStarted(String taskPath, String description) {
        synchronized (dispatchLock) {
            listeners.forEach(listener -> listener.taskStarted(taskPath, description));
        }
    }

    private void notifyTaskProgress(String taskPath, int percentageComplete, String message) {
        synchronized (dispatchLock) {
            listeners.forEach(listener -> listener.taskProgress(taskPath, percentageComplete, message));
        }
    }

    private void notifyTaskCompleted(String taskPath, Store store, String storedName,
                                     Object storedValue, SerializedState serializedState) {
        synchronized (dispatchLock) {
            listeners.forEach(listener -> listener.taskCompleted(taskPath, store, storedName,
                                                                 storedValue, serializedState));
        }
    }

    private void notifyTaskFailed(String taskPath, Throwable error) {
        synchronized (dispatchLock) {
            listeners.forEach(listener -> listener.taskFailed(taskPath, error));
        }
    }

    private void notifyRunCompleted() {
        synchronized (dispatchLock) {
            listeners.forEach(RunListener::runCompleted);
        }
    }

    private void notifyRunFailed(Throwable error) {
        synchronized (dispatchLock) {
            listeners.forEach(listener -> listener.runFailed(error));
        }
    }

    private void notifyRunCancelled() {
        synchronized (dispatchLock) {
            listeners.forEach(RunListener::runCancelled);
        }
    }

}
