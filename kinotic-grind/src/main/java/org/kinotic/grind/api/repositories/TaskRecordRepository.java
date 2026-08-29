package org.kinotic.grind.api.repositories;

import io.vertx.core.Future;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.internal.api.repositories.AbstractRepository;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.kinotic.grind.api.model.TaskRecord;
import org.springframework.stereotype.Component;

@Component
public class TaskRecordRepository extends AbstractRepository<TaskRecord> {

    public TaskRecordRepository(CrudServiceTemplate crudServiceTemplate) {
        super("kinotic_task_record", TaskRecord.class, crudServiceTemplate);
    }

    /**
     * Returns the page of records for the given {@link org.kinotic.grind.api.model.JobRun} id.
     */
    public Future<Page<TaskRecord>> findAllForJobRun(String jobRunId, Pageable pageable) {
        return findAll(pageable, b -> b.query(termFilter("jobRunId", jobRunId)));
    }

}
