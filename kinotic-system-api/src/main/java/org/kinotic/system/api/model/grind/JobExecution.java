package org.kinotic.system.api.model.grind;

import org.kinotic.domain.api.model.grind.Result;
import org.kinotic.domain.api.model.grind.JobRun;

import lombok.Getter;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import java.util.concurrent.atomic.AtomicReference;

/**
 * One recorded execution of a {@link JobDefinition}, pairing the id of its persistent
 * {@link JobRun} record with the {@link Result} stream
 * that performs the run.
 */
public class JobExecution {

    /**
     * The id of the {@link JobRun} recorded for this execution.
     */
    @Getter
    private final String jobRunId;

    /**
     * The stream performing the run. The job executes exactly once no matter how many
     * subscribers attach: execution starts when the first subscriber subscribes, and every
     * subscriber receives the full result history from the beginning, buffered in memory
     * for the lifetime of this {@link JobExecution}.
     * A subscriber cancelling only detaches that subscriber - use {@link #cancel()} to abort the run.
     */
    @Getter
    private final Flux<Result<?>> results;

    private final AtomicReference<Disposable> connection = new AtomicReference<>();

    public JobExecution(String jobRunId, Flux<Result<?>> upstream) {
        this.jobRunId = jobRunId;
        // replay() rather than publish(): a subscriber arriving after completion would otherwise
        // wait forever for a connection that autoConnect has already spent
        this.results = upstream.replay().autoConnect(1, connection::set);
    }

    /**
     * Cancels the run if it has started, recording it as
     * {@link org.kinotic.domain.api.model.grind.ExecutionStatus#CANCELLED}.
     * Does nothing if the run has not started or has already finished.
     */
    public void cancel() {
        Disposable upstreamConnection = connection.get();
        if(upstreamConnection != null){
            upstreamConnection.dispose();
        }
    }

}
