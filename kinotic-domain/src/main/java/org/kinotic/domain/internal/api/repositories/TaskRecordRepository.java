package org.kinotic.domain.internal.api.repositories;

import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.grind.TaskRecord;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
public class TaskRecordRepository extends AbstractRepository<TaskRecord> {

    public TaskRecordRepository(CrudServiceTemplate crudServiceTemplate) {
        super("kinotic_task_record", TaskRecord.class, crudServiceTemplate);
    }

    /**
     * Returns the page of records for the given {@link org.kinotic.domain.api.model.grind.JobRun} id.
     */
    public CompletableFuture<Page<TaskRecord>> findAllForJobRun(String jobRunId, Pageable pageable) {
        return findAll(pageable, b -> b.query(termFilter("jobRunId", jobRunId)));
    }

}
