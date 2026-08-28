package org.kinotic.grindv2.internal.api.services;

import org.kinotic.grindv2.internal.model.SerializedState;
import org.kinotic.grindv2.api.model.JobRunEvent;
import org.kinotic.grindv2.api.model.TaskCompletedEvent;
import org.kinotic.grindv2.api.model.TaskFailedEvent;
import org.kinotic.grindv2.api.model.TaskRecord;
import org.kinotic.grindv2.api.model.TaskStartedEvent;
import org.kinotic.grindv2.api.model.TasksDiscoveredEvent;
import org.kinotic.grindv2.api.model.StoreType;
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
    public void taskCompleted(String taskPath, StoreType storeType, String storedName,
                              Object storedValue, SerializedState serializedState) {
        sink.next(new TaskCompletedEvent(taskPath, storeType, storedName, storedValue));
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
