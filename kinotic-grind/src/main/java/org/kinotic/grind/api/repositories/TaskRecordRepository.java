package org.kinotic.grind.api.repositories;

import co.elastic.clients.elasticsearch._types.FieldValue;
import io.vertx.core.Future;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.internal.api.repositories.AbstractRepository;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.kinotic.grind.api.model.TaskRecord;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

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

    /**
     * Deletes every record of the given {@link org.kinotic.grind.api.model.JobRun}s. Visible to search
     * on completion.
     */
    public Future<Void> deleteAllForJobRuns(Collection<String> jobRunIds) {
        List<FieldValue> ids = jobRunIds.stream().map(FieldValue::of).toList();
        return deleteByQuery(b -> b.query(q -> q.terms(t -> t.field("jobRunId").terms(v -> v.value(ids)))));
    }

}
