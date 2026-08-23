package org.kinotic.os.api.services;

import io.vertx.core.Future;
import org.kinotic.core.api.annotations.Publish;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.grind.JobRun;
import org.kinotic.system.api.model.grind.Result;
import org.kinotic.system.api.model.grind.ResultType;
import org.kinotic.domain.api.model.grind.TaskRecord;
import reactor.core.publisher.Flux;

/**
 * Read access to grind job runs for the authenticated participant: an organization or
 * application participant sees the runs its organization owns, a system participant sees every
 * run. A run's {@link TaskRecord}s are its step ledger - every discovered step has a record,
 * PENDING until it starts executing.
 */
@Publish
public interface JobMonitoringService {

    /**
     * Finds the job runs the participant may view.
     *
     * @param pageable the page of runs to return
     * @return a future that will complete with the page of runs
     */
    Future<Page<JobRun>> findJobRuns(Pageable pageable);

    /**
     * Finds a single job run the participant may view.
     *
     * @param jobRunId the id of the run
     * @return a future that will complete with the run, or fail if the run does not exist
     *         or belongs to another organization
     */
    Future<JobRun> findJobRun(String jobRunId);

    /**
     * Finds the step ledger of a job run the participant may view.
     *
     * @param jobRunId the id of the run
     * @param pageable the page of records to return
     * @return a future that will complete with the page of records, or fail if the run does
     *         not exist or belongs to another organization
     */
    Future<Page<TaskRecord>> findTaskRecords(String jobRunId, Pageable pageable);

    /**
     * Opens a live view of a job run the participant may view, replaying every {@link Result}
     * emitted since the run started and continuing until the run terminates.
     * {@link ResultType#DYNAMIC_STEPS} results carry the discovered steps as PENDING
     * {@link TaskRecord}s in discovery order, {@link ResultType#STEP_FAILED} results carry the
     * failure message, and {@link ResultType#STEP_COMPLETED} and {@link ResultType#VALUE}
     * results carry no produced value.
     *
     * @param jobRunId the id of the run to watch
     * @return the run's {@link Result} stream, empty when the run is not currently executing
     *         in this process
     */
    Flux<Result<?>> watch(String jobRunId);

}
