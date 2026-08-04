package org.kinotic.domain.api.services;

import org.kinotic.core.api.crud.IdentifiableCrudService;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.grind.JobRun;

import java.util.concurrent.CompletableFuture;

/**
 * Service for managing {@link JobRun} entities, the persistent history of grind pipeline executions.
 */
public interface JobRunService extends IdentifiableCrudService<JobRun, String> {

    /**
     * Finds the runs recorded for the given pipeline name.
     * @param pipeline the registered pipeline name
     * @param pageable the page of runs to return
     * @return a future that will complete with the page of runs
     */
    CompletableFuture<Page<JobRun>> findByPipeline(String pipeline, Pageable pageable);

}
