package org.kinotic.grindv2.internal.api.services;

import org.kinotic.grindv2.internal.model.SerializedState;
import org.kinotic.grindv2.api.model.JobRunEvent;
import org.kinotic.grindv2.api.model.StepCompleted;
import org.kinotic.grindv2.api.model.StepFailed;
import org.kinotic.grindv2.api.model.StepRecord;
import org.kinotic.grindv2.api.model.StepStarted;
import org.kinotic.grindv2.api.model.StepsDiscovered;
import org.kinotic.grindv2.api.model.StoreType;
import reactor.core.publisher.FluxSink;

import java.util.List;

/**
 * Republishes the {@link RunListener} callbacks as the run's public {@link JobRunEvent}
 * stream, terminating it with the run.
 */
public class EventStreamAdapter implements RunListener {

    private final FluxSink<JobRunEvent> sink;

    public EventStreamAdapter(FluxSink<JobRunEvent> sink) {
        this.sink = sink;
    }

    @Override
    public void runStarted() {
        // the subscription itself marks the start; the first event follows immediately
    }

    @Override
    public void stepsDiscovered(String parentPath, List<StepRecord> discovered, boolean dynamic) {
        // snapshots rather than the recorder's live records, whose statuses keep changing
        List<StepRecord> snapshot = discovered.stream().map(this::copyOf).toList();
        sink.next(new StepsDiscovered(parentPath, snapshot));
    }

    @Override
    public void stepStarted(String stepPath, String description) {
        sink.next(new StepStarted(stepPath, description));
    }

    @Override
    public void stepCompleted(String stepPath, StoreType storeType, String storedName,
                              Object storedValue, SerializedState serializedState) {
        sink.next(new StepCompleted(stepPath, storeType, storedName, storedValue));
    }

    @Override
    public void stepFailed(String stepPath, Throwable error) {
        sink.next(new StepFailed(stepPath, error.toString()));
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

    private StepRecord copyOf(StepRecord record) {
        return new StepRecord().setId(record.getId())
                               .setJobRunId(record.getJobRunId())
                               .setStepPath(record.getStepPath())
                               .setDescription(record.getDescription())
                               .setStatus(record.getStatus());
    }

}
