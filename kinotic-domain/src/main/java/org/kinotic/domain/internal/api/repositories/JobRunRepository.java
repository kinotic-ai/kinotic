package org.kinotic.domain.internal.api.repositories;

import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.grind.JobRun;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
public class JobRunRepository extends AbstractRepository<JobRun> {

    public JobRunRepository(CrudServiceTemplate crudServiceTemplate) {
        super("kinotic_job_run", JobRun.class, crudServiceTemplate);
    }

    /**
     * Returns the page of runs recorded for the given pipeline name.
     */
    public CompletableFuture<Page<JobRun>> findByPipeline(String pipeline, Pageable pageable) {
        return findAll(pageable, b -> b.query(termFilter("pipeline", pipeline)));
    }

}
