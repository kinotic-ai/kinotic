package org.kinotic.management.api.services;

import org.kinotic.management.api.model.grind.Result;
import org.kinotic.management.api.model.grind.ResultType;
import org.kinotic.management.api.model.grind.TaskRecord;
import reactor.core.publisher.Flux;

/**
 * Live view of grind job runs executing in this process. The full job engine lives in
 * kinotic-system-api; this seam exposes only watching, so modules that monitor runs need
 * no dependency on the engine.
 */
public interface JobWatchService {

    /**
     * Opens a view of a run currently executing in this process. The returned {@link Flux} replays
     * every {@link Result} emitted since the run started, then continues live until the run
     * terminates. Watching never starts a run - subscribing attaches to the in-flight execution only.
     * <p>
     * Result values are reduced to what a monitoring caller can consume remotely:
     * {@link ResultType#DYNAMIC_STEPS} carries the discovered steps as PENDING
     * {@link TaskRecord}s in discovery order,
     * {@link ResultType#STEP_FAILED} carries the failure message, and
     * {@link ResultType#STEP_COMPLETED} and {@link ResultType#VALUE} carry no produced value.
     * @param jobRunId the id of the run to watch
     * @return the run's {@link Result} stream, or an empty {@link Flux} when no run with the
     *         given id is executing in this process
     */
    Flux<Result<?>> watchExecution(String jobRunId);

}
