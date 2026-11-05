package org.kinotic.grind.internal.api.services;

import org.kinotic.grind.internal.api.model.DefaultJobContext;
import org.kinotic.grind.internal.api.model.DefaultJobDefinition;
import org.kinotic.grind.internal.model.DefinitionNode;
import org.kinotic.grind.internal.model.ReplayEntry;
import org.kinotic.grind.internal.model.RunCancelledException;
import org.kinotic.grind.internal.model.SerializedState;
import org.kinotic.grind.internal.model.JobNode;
import org.kinotic.grind.internal.model.TaskNode;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.internal.ContextInternal;
import lombok.SneakyThrows;
import org.apache.commons.lang3.Validate;
import org.kinotic.grind.api.model.ExecutionStatus;
import org.kinotic.grind.api.model.JobDefinition;
import org.kinotic.grind.api.model.JobScope;
import org.kinotic.grind.api.model.TaskReporter;
import org.kinotic.grind.api.model.TaskView;
import org.kinotic.grind.api.model.ViewProducer;
import org.kinotic.grind.api.model.TaskRecord;
import org.kinotic.grind.api.model.Store;
import org.kinotic.grind.api.model.StoreType;
import org.kinotic.grind.api.model.Task;
import org.reactivestreams.Publisher;
import org.springframework.context.ConfigurableApplicationContext;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
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
        notifyTaskStarted(path, definition.getDescription(), null);
        boolean destroyOnExit = definition.getScope() == JobScope.CHILD;
        DefaultJobContext scope = destroyOnExit ? parentScope.createChild() : parentScope;
        try {
            if (definition.isParallel()) {
                executeChildrenParallel(path, scope, definition.getTasks());
            } else {
                executeChildrenInTurn(path, scope, definition);
            }
            notifyTaskCompleted(path, Store.none(), null, null, null, null);
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
            // a skipped task keeps what it showed and what it published when the original run completed it
            SerializedState wired = entry.wireValue() != null ? new SerializedState(null, entry.wireValue()) : null;
            notifyTaskStarted(path, node.description(), null);
            notifyTaskCompleted(path, node.store(), null, null, wired, entry.view());
            return;
        }
        try {
            if (completedInOriginal && node.store().getType() == StoreType.STATE && entry.value() != null) {
                replayState(path, scope, node, entry);
            } else {
                runTask(path, scope, node, completedInOriginal);
            }
        } catch (Throwable t) {
            handleTaskFailure(path, t);
        }
    }

    /**
     * Restores a completed task's durable state instead of executing it. Its declared views are
     * built as an execution would build them, from the scope and the replayed value; a task that
     * declares no result view ends on what it showed last when the original run completed it.
     */
    private void replayState(String path, DefaultJobContext scope, TaskNode node, ReplayEntry entry) {
        startTask(path, scope, node);
        String storedName = ScopeWriter.store(scope, node.store().getType(), node.store().getName(), entry.value());
        SerializedState state = stateSerializer.serialize(node.description(), entry.value());
        JsonNode view = node.resultView() != null
                ? buildView(node, node.resultView(), entry.value(), scope)
                : entry.view();
        notifyTaskCompleted(path, node.store(), storedName, entry.value(), state, view);
    }

    private void runTask(String path, DefaultJobContext scope, TaskNode node, boolean completedInOriginal) throws Exception {
        // RESULT reloads from its source of truth: the declared reload task when there is one,
        // otherwise the task itself, which must then be safe to re-run
        Task<?> taskToRun = completedInOriginal && node.store().getReloadTask() != null
                ? node.store().getReloadTask() : node.task();
        startTask(path, scope, node);
        // the task executes in a scope of its own holding its TaskReporter, which stays in flight until
        // the result is awaited so callbacks of the task's futures report for it; only a Task or
        // JobDefinition the task returns directly is dynamic work, one a future completes with is a value
        AtomicBoolean inFlight = new AtomicBoolean(true);
        Object raw;
        Object result;
        try {
            raw = taskToRun.execute(scope.createTaskScope(taskReporter(path, node.description(), runContext(), inFlight)));
            result = raw instanceof Task<?> || raw instanceof JobDefinition ? raw : awaitValue(raw);
        } finally {
            inFlight.set(false);
        }
        if (raw instanceof Task<?> dynamicTask) {
            // A dynamic task that declares no store of its own keeps the value as this task's store does
            Store dynamicStore = dynamicTask.getStore() != null ? dynamicTask.getStore() : node.store();
            TaskNode dynamicNode = new TaskNode(1, dynamicTask, dynamicStore);
            discoverDynamic(path, dynamicNode);
            executeTask(path + "/1", scope, dynamicNode);
            notifyTaskCompleted(path, Store.none(), null, null, null, null);
        } else if (raw instanceof JobDefinition dynamicDefinition) {
            DefinitionNode dynamicNode = new DefinitionNode(1, (DefaultJobDefinition) dynamicDefinition);
            discoverDynamic(path, dynamicNode);
            executeDefinition(path + "/1", scope, dynamicNode.definition());
            notifyTaskCompleted(path, Store.none(), null, null, null, null);
        } else {
            JsonNode view = buildView(node, node.resultView(), result, scope);
            String storedName = ScopeWriter.store(scope, node.store().getType(), node.store().getName(), result);
            notifyTaskCompleted(path, node.store(), storedName, result, serializeIfNeeded(node, result), view);
        }
    }

    /**
     * Reports the task started, with the view it declares for its start. A view that cannot be
     * built fails the task once it is reported started, so its record shows where it failed.
     */
    private void startTask(String path, DefaultJobContext scope, TaskNode node) {
        JsonNode view;
        try {
            view = buildView(node, node.startView(), null, scope);
        } catch (RuntimeException e) {
            notifyTaskStarted(path, node.description(), null);
            throw e;
        }
        notifyTaskStarted(path, node.description(), view);
    }

    /**
     * Builds a declared view as JSON, or returns null for none.
     * @throws IllegalStateException when the scope holds no value of the view's source type, or the
     *         view does not serialize
     */
    private JsonNode buildView(TaskNode node, ViewProducer producer, Object result, DefaultJobContext scope) {
        JsonNode ret = null;
        if (producer != null) {
            Object source = null;
            if (producer.source() != null) {
                source = scope.getBeanOrNull(producer.source());
                if (source == null) {
                    throw new IllegalStateException("Task '" + node.description() + "' shows a view built from a "
                            + producer.source().getSimpleName() + " but nothing in the job scope provides one");
                }
            }
            ret = stateSerializer.serializeView(node.description(), producer.producer().apply(result, source));
        }
        return ret;
    }

    // A duplicate of the run context shares the run's task, so it resolves to the context it wraps
    private Context runContext() {
        Context current = Vertx.currentContext();
        return current instanceof ContextInternal internal ? internal.unwrap() : current;
    }

    /**
     * The {@link TaskReporter} one task is given: its reports attach to that task while it is in
     * flight, from its body or a callback dispatched on the run context it executes on.
     */
    private TaskReporter taskReporter(String path, String description, Context context, AtomicBoolean inFlight) {
        return new TaskReporter() {
            @Override
            public void progress(int percentageComplete, String message) {
                if (accepts()) {
                    notifyTaskProgress(path, percentageComplete, message);
                }
            }

            @Override
            public void view(TaskView<?> view) {
                Validate.notNull(view, "view cannot be null");
                if (accepts()) {
                    notifyTaskViewed(path, stateSerializer.serializeView(description, view));
                }
            }

            // a report blocks the caller on the ledger write, which only the run's own virtual-thread
            // context may do, so a report from a thread the task spawned is dropped
            private boolean accepts() {
                return inFlight.get() && runContext() == context;
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

    // Runs the children one after another. A definition continuing on failure runs every child and
    // fails with the first failure once all have run; any other stops at the first failure
    private void executeChildrenInTurn(String parentPath, DefaultJobContext scope, DefaultJobDefinition definition) {
        Throwable firstError = null;
        for (JobNode child : definition.getTasks()) {
            try {
                executeChild(parentPath, scope, child);
            } catch (RunCancelledException e) {
                throw e;
            } catch (Throwable t) {
                if (!definition.isContinueOnFailure()) {
                    rethrow(t);
                } else if (firstError == null) {
                    firstError = t;
                }
            }
        }
        if (firstError != null) {
            rethrow(firstError);
        }
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
        } else if (raw instanceof Future<?> future) {
            ret = RunThread.await(future);
        } else if (raw instanceof Publisher<?> publisher) {
            ret = awaitPublisher(publisher);
        } else {
            ret = raw;
        }
        return ret;
    }

    private Object awaitStage(CompletionStage<?> stage) {
        Promise<Object> bridged = Promise.promise();
        stage.whenComplete((value, error) -> {
            if (error != null) {
                // a failure that crossed a dependent stage arrives CompletionException-wrapped;
                // strip it so the task sees the raw cause
                bridged.fail(error instanceof CompletionException && error.getCause() != null
                                     ? error.getCause() : error);
            } else {
                bridged.complete(value);
            }
        });
        return RunThread.await(bridged.future());
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
        // a task whose blocking call the cancellation interrupted fails with an exception of its own,
        // and is cancelled all the same
        if (!(error instanceof RunCancelledException) && RunThread.cancelled()) {
            throw new RunCancelledException();
        }
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
        TaskRecord record = new TaskRecord().setId(runId + ":" + path)
                                            .setJobRunId(runId)
                                            .setTaskPath(path)
                                            .setDescription(node.description())
                                            .setStatus(ExecutionStatus.PENDING);
        if (node instanceof TaskNode taskNode) {
            record.setIcon(taskNode.icon());
            // A store named in the definition is a fact of the task, not of its completion:
            // recorded now so a watcher can tell the tasks apart by what they will store
            if (taskNode.store().getName() != null) {
                record.setStoreType(taskNode.store().getType())
                      .setStoredName(taskNode.store().getName());
            }
        }
        collected.add(record);
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

    private void notifyTaskStarted(String taskPath, String description, JsonNode view) {
        synchronized (dispatchLock) {
            listeners.forEach(listener -> listener.taskStarted(taskPath, description, view));
        }
    }

    private void notifyTaskProgress(String taskPath, int percentageComplete, String message) {
        synchronized (dispatchLock) {
            listeners.forEach(listener -> listener.taskProgress(taskPath, percentageComplete, message));
        }
    }

    private void notifyTaskViewed(String taskPath, JsonNode view) {
        synchronized (dispatchLock) {
            listeners.forEach(listener -> listener.taskViewed(taskPath, view));
        }
    }

    private void notifyTaskCompleted(String taskPath, Store store, String storedName,
                                     Object storedValue, SerializedState serializedState, JsonNode view) {
        synchronized (dispatchLock) {
            listeners.forEach(listener -> listener.taskCompleted(taskPath, store, storedName,
                                                                 storedValue, serializedState, view));
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
