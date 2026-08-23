package org.kinotic.domain.api.services;

import io.vertx.core.Future;
import org.kinotic.core.api.crud.IdentifiableCrudService;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.grind.JobRun;
import org.kinotic.domain.api.model.grind.TaskRecord;

/**
 * Service for managing {@link TaskRecord} entities, the per-step history of a {@link JobRun}.
 */
public interface TaskRecordService extends IdentifiableCrudService<TaskRecord, String> {

    /**
     * Finds the records for the given {@link JobRun} id.
     * @param jobRunId the id of the run
     * @param pageable the page of records to return
     * @return a future that will complete with the page of records
     */
    Future<Page<TaskRecord>> findAllForJobRun(String jobRunId, Pageable pageable);

}
