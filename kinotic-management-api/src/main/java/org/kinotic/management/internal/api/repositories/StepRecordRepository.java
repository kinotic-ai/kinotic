package org.kinotic.management.internal.api.repositories;

import org.kinotic.domain.internal.api.repositories.AbstractRepository;

import io.vertx.core.Future;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.management.api.model.grind.StepRecord;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.springframework.stereotype.Component;

@Component
public class StepRecordRepository extends AbstractRepository<StepRecord> {

    public StepRecordRepository(CrudServiceTemplate crudServiceTemplate) {
        super("kinotic_step_record", StepRecord.class, crudServiceTemplate);
    }

    /**
     * Returns the page of records for the given {@link org.kinotic.management.api.model.grind.JobRun} id.
     */
    public Future<Page<StepRecord>> findAllForJobRun(String jobRunId, Pageable pageable) {
        return findAll(pageable, b -> b.query(termFilter("jobRunId", jobRunId)));
    }

}
