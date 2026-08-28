package org.kinotic.grind.internal.api.services;

import org.kinotic.grind.internal.model.SerializedState;
import org.kinotic.grind.api.model.events.JobRunEvent;
import org.kinotic.grind.api.model.events.TaskCompletedEvent;
import org.kinotic.grind.api.model.events.TaskFailedEvent;
import org.kinotic.grind.api.model.events.TaskProgressEvent;
import org.kinotic.grind.api.model.TaskRecord;
import org.kinotic.grind.api.model.events.TaskStartedEvent;
import org.kinotic.grind.api.model.events.TasksDiscoveredEvent;
import org.kinotic.grind.api.model.Store;
import reactor.core.publisher.FluxSink;

import java.util.List;

/**
 * Republishes the {@link RunListener} callbacks as the run's public {@link JobRunEvent}
 * stream, terminating it with the run.
 */
public class RunEventEmitter implements RunListener {

    private final FluxSink<JobRunEvent> sink;

    public RunEventEmitter(FluxSink<JobRunEvent> sink) {
        this.sink = sink;
    }

    @Override
    public void runStarted() {
        // the subscription itself marks the start; the first event follows immediately
    }

    @Override
    public void tasksDiscovered(String parentPath, List<TaskRecord> discovered, boolean dynamic) {
        // snapshots rather than the recorder's live records, whose statuses keep changing
        List<TaskRecord> snapshot = discovered.stream().map(this::copyOf).toList();
        sink.next(new TasksDiscoveredEvent(parentPath, snapshot));
    }

    @Override
    public void taskStarted(String taskPath, String description) {
        sink.next(new TaskStartedEvent(taskPath, description));
    }

    @Override
    public void taskProgress(String taskPath, int percentageComplete, String message) {
        sink.next(new TaskProgressEvent(taskPath, percentageComplete, message));
    }

    @Override
    public void taskCompleted(String taskPath, Store store, String storedName,
                              Object storedValue, SerializedState serializedState) {
        sink.next(new TaskCompletedEvent(taskPath, store.getType(), storedName, storedValue,
                                         store.isWire() && serializedState != null ? serializedState.value() : null));
    }

    @Override
    public void taskFailed(String taskPath, Throwable error) {
        sink.next(new TaskFailedEvent(taskPath, error.toString()));
    }

    @Override
    public void runCompleted() {
        sink.complete();
    }

    @Override
    public void runFailed(Throwable error) {
        sink.error(error);
    }

    @Override
    public void runCancelled() {
        sink.complete();
    }

    private TaskRecord copyOf(TaskRecord record) {
        return new TaskRecord().setId(record.getId())
                               .setJobRunId(record.getJobRunId())
                               .setTaskPath(record.getTaskPath())
                               .setDescription(record.getDescription())
                               .setStatus(record.getStatus());
    }

}
