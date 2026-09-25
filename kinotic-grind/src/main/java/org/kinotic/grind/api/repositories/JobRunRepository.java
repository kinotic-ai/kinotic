package org.kinotic.grind.api.repositories;

import co.elastic.clients.elasticsearch._types.FieldValue;
import io.vertx.core.Future;
import org.apache.commons.lang3.Validate;
import org.kinotic.domain.api.model.StatusCondition;
import org.kinotic.domain.api.repositories.WatchedRepository;
import org.kinotic.domain.api.model.WatchedType;
import org.kinotic.domain.api.model.WatchEventKind;
import org.kinotic.domain.internal.api.repositories.WatchedChange;
import org.kinotic.domain.internal.api.repositories.WatchedDocument;
import org.kinotic.domain.internal.api.repositories.WatchedIndex;
import org.kinotic.domain.internal.api.repositories.WatchedStateRepository;
import org.kinotic.grind.api.model.ExecutionStatus;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.internal.api.repositories.AbstractRepository;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.kinotic.grind.api.model.JobOwner;
import org.kinotic.grind.api.model.JobRun;
import org.kinotic.grind.api.model.TaskRecord;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Repository for the run ledger: each {@link JobRun} and its per-task {@link TaskRecord}s.
 * The engine writes through it as a run executes and reads it back to resume a failed run.
 * Runs live in the {@code kinotic_job_run} index this repository owns, their task records in
 * the {@link TaskRecordRepository}'s.
 */
@Component
public class JobRunRepository extends AbstractRepository<JobRun> implements WatchedRepository<JobRun> {

    public static final WatchedIndex WATCHED = new WatchedIndex(WatchedType.JOB_RUN, "kinotic_job_run");

    private static final int RECORD_PAGE_SIZE = 500;

    // The run's outcome is the executing node's; the state the platform keeps is touched the way
    // every other write touches it, in the same shard operation
    private static final String RECORD_OUTCOME = WatchedStateRepository.STATE_FUNCTIONS + """
            ctx._source.status = params.status;
            ctx._source.error = params.error;
            ctx._source.finished = params.finished;
            touched(state(ctx._source), params.now);
            """;

    private final TaskRecordRepository taskRecordRepository;
    private final WatchedStateRepository watchedStateRepository;

    public JobRunRepository(CrudServiceTemplate crudServiceTemplate,
                            TaskRecordRepository taskRecordRepository,
                            WatchedStateRepository watchedStateRepository) {
        super(WATCHED.name(), JobRun.class, crudServiceTemplate);
        this.taskRecordRepository = taskRecordRepository;
        this.watchedStateRepository = watchedStateRepository;
    }

    @Override
    public WatchedType type() {
        return WATCHED.type();
    }

    // Stored by id alone; the organization is what the repository of the deployment a run was made
    // by needs to find that
    @Override
    public String scopeOf(JobRun record) {
        return record.getOrganizationId();
    }

    @Override
    public Future<JobRun> find(String id, String scope) {
        return findById(id);
    }

    @Override
    public Future<Page<JobRun>> findDirty(Pageable pageable) {
        return watchedStateRepository.findDirty(indexName, type, pageable);
    }

    @Override
    public Future<Void> clearDirty(String id, String scope, long dirtyAt) {
        return watchedStateRepository.clearDirty(WatchedDocument.of(WATCHED, id), dirtyAt);
    }

    /**
     * Records the run's terminal status, its error and when it finished, and enters the change in the
     * ledger, leaving every other field as it is; visible to search on completion.
     *
     * @param jobRunId the run
     * @param status   the run's terminal status
     * @param error    why it failed, or null
     * @param finished when it finished
     * @param source   what caused it, for the ledger
     */
    public Future<Void> recordOutcome(String jobRunId, ExecutionStatus status, String error, Date finished, String source) {
        Validate.notBlank(jobRunId, "jobRunId cannot be blank");
        Validate.notNull(status, "status cannot be null");
        Validate.notNull(finished, "finished cannot be null");
        Map<String, Object> params = new HashMap<>();
        params.put("status", status.name());
        params.put("error", error);
        params.put("finished", finished.toInstant().toString());
        params.put("now", System.currentTimeMillis());
        return crudServiceTemplate.scriptedUpdateReturningSourceSync(indexName, jobRunId, RECORD_OUTCOME, params)
                                  .compose(document -> watchedStateRepository.record(
                                          WatchedDocument.of(WATCHED, jobRunId), document,
                                          new WatchedChange(WatchEventKind.STATUS_CHANGED, source, "Run " + status, status)));
    }

    /**
     * @see WatchedStateRepository#setCondition(WatchedDocument, StatusCondition, String)
     */
    public Future<Boolean> setCondition(String jobRunId, StatusCondition condition, String source) {
        return watchedStateRepository.setCondition(WatchedDocument.of(WATCHED, jobRunId), condition, source);
    }

    /**
     * The runs still executing on nodes other than the given ones: what a node that left the cluster
     * leaves behind.
     *
     * @param members  the nodes in the cluster
     * @param pageable the page to return
     */
    public Future<Page<JobRun>> findRunningElsewhere(Set<String> members, Pageable pageable) {
        Validate.notNull(members, "members cannot be null");
        List<FieldValue> nodeIds = members.stream().map(FieldValue::of).toList();
        return findAll(pageable, b -> b.query(q -> q.bool(bool -> bool
                .filter(termFilter("status", ExecutionStatus.RUNNING.name()))
                .mustNot(mn -> mn.terms(t -> t.field("nodeId").terms(v -> v.value(nodeIds)))))));
    }

    /**
     * Saves the given run, creating or updating its record.
     * @param jobRun the run to save
     * @return a future that will complete with the saved run
     */
    public Future<JobRun> saveRun(JobRun jobRun) {
        return save(jobRun);
    }

    /**
     * Saves the given task record, creating or updating it.
     * @param taskRecord the record to save
     * @return a future that will complete with the saved record
     */
    public Future<TaskRecord> saveTask(TaskRecord taskRecord) {
        return taskRecordRepository.save(taskRecord);
    }

    /**
     * Finds a run by id.
     * @param jobRunId the id of the run
     * @return a future that will complete with the run, or {@code null} when none exists
     */
    public Future<JobRun> findRun(String jobRunId) {
        return findById(jobRunId);
    }

    /**
     * Finds every task record of the given run.
     * @param jobRunId the id of the run
     * @return a future that will complete with the run's records
     */
    public Future<List<TaskRecord>> findTasks(String jobRunId) {
        List<TaskRecord> collected = new ArrayList<>();
        return readTaskPage(jobRunId, 0, collected).map(collected);
    }

    /**
     * Returns the page of runs owned by the given {@link JobOwner}: all of an organization's
     * runs, narrowed to an application and/or project when the owner carries those ids. The
     * system owner selects platform runs - those owned by no organization.
     */
    public Future<Page<JobRun>> findAllForOwner(JobOwner owner, Pageable pageable) {
        return findAll(pageable, b -> b.query(composeFilter(
            owner.getOrganizationId() != null ? termFilter("organizationId", owner.getOrganizationId())
                                              : missingFilter("organizationId"),
            owner.getApplicationId() != null ? termFilter("applicationId", owner.getApplicationId()) : null,
            owner.getProjectId() != null ? termFilter("projectId", owner.getProjectId()) : null)));
    }

    private Future<Void> readTaskPage(String jobRunId, int pageNumber, List<TaskRecord> collected) {
        return taskRecordRepository.findAllForJobRun(jobRunId, Pageable.create(pageNumber, RECORD_PAGE_SIZE, null))
                                   .compose(page -> {
                                       collected.addAll(page.getContent());
                                       Future<Void> ret;
                                       if(page.getContent().size() < RECORD_PAGE_SIZE){
                                           ret = Future.succeededFuture();
                                       }else{
                                           ret = readTaskPage(jobRunId, pageNumber + 1, collected);
                                       }
                                       return ret;
                                   });
    }

}
