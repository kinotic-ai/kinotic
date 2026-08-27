package org.kinotic.grindv2.api;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import lombok.Getter;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import java.util.concurrent.atomic.AtomicReference;

/**
 * One recorded execution of a {@link JobDefinition}, pairing the id of its persistent
 * {@link JobRun} record with the run's {@link JobRunEvent} stream.
 */
public class JobRunHandle {

    /**
     * The id of the {@link JobRun} recorded for this execution.
     */
    @Getter
    private final String jobRunId;

    /**
     * The run's event stream. The job executes exactly once no matter how many subscribers
     * attach: execution starts when the first subscriber subscribes, and every subscriber
     * receives the full event history from the beginning, buffered in memory for the lifetime
     * of this handle. A subscriber cancelling only detaches that subscriber - use
     * {@link #cancel()} to abort the run.
     */
    @Getter
    private final Flux<JobRunEvent> events;

    private final AtomicReference<Disposable> connection = new AtomicReference<>();

    public JobRunHandle(String jobRunId, Flux<JobRunEvent> upstream) {
        this.jobRunId = jobRunId;
        // replay() rather than publish(): a subscriber arriving after completion would otherwise
        // wait forever for a connection that autoConnect has already spent
        this.events = upstream.replay().autoConnect(1, connection::set);
    }

    /**
     * Completes when the run terminates: successfully, or failed with the run's error.
     * Calling this subscribes to {@link #getEvents()}, starting the run if it has not started.
     * @return the terminal future
     */
    public Future<Void> completion() {
        Promise<Void> promise = Promise.promise();
        events.subscribe(event -> { },
                         promise::tryFail,
                         promise::tryComplete);
        return promise.future();
    }

    /**
     * Cancels the run if it has started, recording it as {@link ExecutionStatus#CANCELLED}.
     * Does nothing if the run has not started or has already finished.
     */
    public void cancel() {
        Disposable upstreamConnection = connection.get();
        if (upstreamConnection != null) {
            upstreamConnection.dispose();
        }
    }

}
