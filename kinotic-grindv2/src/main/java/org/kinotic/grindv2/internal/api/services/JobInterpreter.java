package org.kinotic.grindv2.internal.api.services;

import org.kinotic.grindv2.internal.api.model.DefaultJobContext;
import org.kinotic.grindv2.internal.api.model.DefaultJobDefinition;
import org.kinotic.grindv2.internal.model.DefinitionNode;
import org.kinotic.grindv2.internal.model.ReplayEntry;
import org.kinotic.grindv2.internal.model.RunCancelledException;
import org.kinotic.grindv2.internal.model.SerializedState;
import org.kinotic.grindv2.internal.model.JobNode;
import org.kinotic.grindv2.internal.model.TaskNode;
import lombok.SneakyThrows;
import org.kinotic.grindv2.api.model.ExecutionStatus;
import org.kinotic.grindv2.api.model.JobDefinition;
import org.kinotic.grindv2.api.model.JobScope;
import org.kinotic.grindv2.api.model.TaskRecord;
import org.kinotic.grindv2.api.model.StoreType;
import org.kinotic.grindv2.api.model.Task;
import org.reactivestreams.Publisher;
import org.springframework.context.ConfigurableApplicationContext;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
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
    // Serializes listener dispatch so parallel definitions cannot interleave callbacks
    private final Object dispatchLock = new Object();

    public JobInterpreter(ConfigurableApplicationContext applicationContext,
                          String runId,
                          DefaultJobDefinition rootDefinition,
                          List<RunListener> listeners,
                          Map<String, ReplayEntry> replay,
                          StateSerializer stateSerializer) {
        this.applicationContext = applicationContext;
        this.runId = runId;
        this.rootDefinition = rootDefinition;
        this.listeners = listeners;
        this.replay = replay;
        this.stateSerializer = stateSerializer;
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
            seedInputs(rootScope);
            executeDefinitionTask("0", rootScope, rootDefinition);
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

    private void executeDefinitionTask(String path, DefaultJobContext parentScope, DefaultJobDefinition definition) {
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
            notifyTaskCompleted(path, StoreType.NONE, null, null, null);
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
            executeTaskTask(childPath, scope, taskNode);
        } else if (child instanceof DefinitionNode definitionNode) {
            executeDefinitionTask(childPath, scope, definitionNode.definition());
        }
    }

    private void executeTaskTask(String path, DefaultJobContext scope, TaskNode node) {
        ReplayEntry entry = replay.get(path);
        // A task that produced dynamic tasks must re-execute so they are regenerated;
        // the regenerated tasks then consult the replay entries at their own paths
        boolean completedInOriginal = entry != null && !entry.dynamicTasks();

        if (completedInOriginal && node.storeType() == StoreType.NONE) {
            notifyTaskStarted(path, node.description());
            notifyTaskCompleted(path, StoreType.NONE, null, null, null);
            return;
        }
        if (completedInOriginal && node.storeType() == StoreType.STATE && entry.value() != null) {
            notifyTaskStarted(path, node.description());
            String storedName = ScopeWriter.store(scope, node.storeType(), node.resultName(), entry.value());
            SerializedState state = stateSerializer.serialize(node.description(), entry.value());
            notifyTaskCompleted(path, StoreType.STATE, storedName, entry.value(), state);
            return;
        }

        // RESULT reloads from its source of truth: the declared reload task when there is one,
        // otherwise the task itself, which must then be safe to re-run
        Task<?> taskToRun = completedInOriginal && node.reloadTask() != null ? node.reloadTask() : node.task();
        notifyTaskStarted(path, node.description());
        try {
            Object raw = taskToRun.execute(scope);
            if (raw instanceof Task<?> dynamicTask) {
                // The dynamic task carries this task's store settings, so it stores the value
                TaskNode dynamicNode = new TaskNode(1, dynamicTask, node.reloadTask(), node.storeType(), node.resultName());
                discoverDynamic(path, dynamicNode);
                executeTaskTask(path + "/1", scope, dynamicNode);
                notifyTaskCompleted(path, StoreType.NONE, null, null, null);
            } else if (raw instanceof JobDefinition dynamicDefinition) {
                DefinitionNode dynamicNode = new DefinitionNode(1, (DefaultJobDefinition) dynamicDefinition);
                discoverDynamic(path, dynamicNode);
                executeDefinitionTask(path + "/1", scope, dynamicNode.definition());
                notifyTaskCompleted(path, StoreType.NONE, null, null, null);
            } else {
                Object value = awaitValue(raw);
                String storedName = ScopeWriter.store(scope, node.storeType(), node.resultName(), value);
                SerializedState state = node.storeType() == StoreType.STATE
                        ? stateSerializer.serialize(node.description(), value) : null;
                notifyTaskCompleted(path, node.storeType(), storedName, value, state);
            }
        } catch (Throwable t) {
            handleTaskFailure(path, t);
        }
    }

    private void executeChildrenParallel(String parentPath, DefaultJobContext scope, List<JobNode> children) {
        List<Thread> threads = new ArrayList<>();
        AtomicReference<Throwable> firstError = new AtomicReference<>();
        for (JobNode child : children) {
            threads.add(Thread.ofVirtual()
                              .name("grindv2-" + runId + "-" + parentPath + "/" + child.sequence())
                              .unstarted(() -> {
                                  try {
                                      executeChild(parentPath, scope, child);
                                  } catch (Throwable t) {
                                      // first failure wins and cancels the siblings; a sibling's own
                                      // RunCancelledException from that interrupt is swallowed here
                                      if (firstError.compareAndSet(null, t)) {
                                          interruptOthers(threads);
                                      }
                                  }
                              }));
        }
        threads.forEach(Thread::start);
        joinAll(threads);
        Throwable error = firstError.get();
        if (error != null) {
            rethrow(error);
        }
    }

    private void interruptOthers(List<Thread> threads) {
        for (Thread thread : threads) {
            if (thread != Thread.currentThread()) {
                thread.interrupt();
            }
        }
    }

    private void joinAll(List<Thread> threads) {
        boolean cancelled = false;
        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                // the run itself was cancelled: stop the children, then keep joining so no
                // child outlives the run
                cancelled = true;
                threads.forEach(Thread::interrupt);
                joinUninterruptibly(thread);
            }
        }
        if (cancelled) {
            throw new RunCancelledException();
        }
    }

    private void joinUninterruptibly(Thread thread) {
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

    private void notifyTaskCompleted(String taskPath, StoreType storeType, String storedName,
                                     Object storedValue, SerializedState serializedState) {
        synchronized (dispatchLock) {
            listeners.forEach(listener -> listener.taskCompleted(taskPath, storeType, storedName,
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
