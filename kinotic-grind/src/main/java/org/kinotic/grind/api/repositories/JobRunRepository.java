package org.kinotic.grind.api.repositories;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import io.vertx.core.Future;
import org.apache.commons.lang3.Validate;
import org.kinotic.core.api.crud.Page;
import org.kinotic.core.api.crud.Pageable;
import org.kinotic.domain.api.model.StatusCondition;
import org.kinotic.domain.api.model.StatusConditionType;
import org.kinotic.domain.api.model.WatchEventType;
import org.kinotic.domain.api.model.WatchedType;
import org.kinotic.domain.internal.api.repositories.AbstractWatchedRepository;
import org.kinotic.domain.internal.api.repositories.WatchEventRepository;
import org.kinotic.domain.internal.api.repositories.WatchedChange;
import org.kinotic.domain.internal.api.repositories.WatchedIndex;
import org.kinotic.domain.internal.api.repositories.WatchedStateRepository;
import org.kinotic.domain.internal.api.services.CrudServiceTemplate;
import org.kinotic.grind.api.model.ExecutionStatus;
import org.kinotic.grind.api.model.JobRun;
import org.kinotic.grind.api.model.JobRunFilter;
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
public class JobRunRepository extends AbstractWatchedRepository<JobRun> {

    private static final WatchedIndex WATCHED = new WatchedIndex(WatchedType.JOB_RUN, "kinotic_job_run");

    private static final int RECORD_PAGE_SIZE = 500;

    // The run's outcome is the executing node's; the state the platform keeps is touched the way
    // every other write touches it, in the same shard operation
    // An outcome disproves a SERVER_NODE_LEFT mark: the node that left came back and finished the run
    private static final String RECORD_OUTCOME = WatchedStateRepository.STATE_FUNCTIONS + """
            ctx._source.status = params.status;
            ctx._source.error = params.error;
            ctx._source.finished = params.finished;
            def s = state(ctx._source);
            def left = condition(s, params.nodeLeft);
            if (left != null) {
                s.conditions.remove(s.conditions.indexOf(left));
            }
            touched(ctx._source, s, params);
            """;

    // A mark says the run is still executing on a node that left, so a run that has finished declines
    // it: the watcher marks the runs a search found executing, and a run that finished between the
    // search and the write keeps its outcome
    private static final String SET_CONDITION_ON_EXECUTING_RUN = WatchedStateRepository.STATE_FUNCTIONS + """
            if (ctx._source.status == params.executing) {
            """ + WatchedStateRepository.ADD_CONDITION + """
            } else {
                ctx.op = 'noop';
            }
            """;

    private final TaskRecordRepository taskRecordRepository;

    public JobRunRepository(CrudServiceTemplate crudServiceTemplate,
                            TaskRecordRepository taskRecordRepository,
                            WatchedStateRepository watchedStateRepository,
                            WatchEventRepository watchEventRepository) {
        super(WATCHED, JobRun.class, crudServiceTemplate, watchedStateRepository, watchEventRepository);
        this.taskRecordRepository = taskRecordRepository;
    }

    @Override
    public String scopeOf(JobRun record) {
        return record.getOrganizationId();
    }

    /**
     * Sets the condition on the run while it is executing and records it; a run that has finished
     * keeps its outcome and declines the condition, as does a run already carrying one of its type.
     * Visible to search on completion.
     *
     * @param id        the run
     * @param condition the condition to set
     * @param source    what caused it, for the ledger
     * @return true when the condition was set
     */
    @Override
    public Future<Boolean> setCondition(String id, StatusCondition condition, String source) {
        return watchedStateRepository.setCondition(document(id), condition, source, SET_CONDITION_ON_EXECUTING_RUN,
                                                   Map.of("executing", ExecutionStatus.RUNNING.name()));
    }

    /**
     * Records the run's terminal status, its error and when it finished, and enters the change in the
     * ledger, leaving every other field as it is; visible to search on completion.
     *
     * A SERVER_NODE_LEFT mark goes with it: the node that left came back and finished the run.
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
        params.put("nodeLeft", StatusConditionType.SERVER_NODE_LEFT.name());
        return watchedStateRepository.write(document(jobRunId), RECORD_OUTCOME, params,
                                            new WatchedChange(WatchEventType.STATUS_CHANGED, source, "Run " + status, status))
                                     .mapEmpty();
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
     * Returns the page of runs the given filter matches.
     *
     * @param filter   which runs to return
     * @param pageable the page to return
     */
    public Future<Page<JobRun>> findAllMatching(JobRunFilter filter, Pageable pageable) {
        Validate.notNull(filter, "filter cannot be null");
        return findAll(pageable, b -> b.query(composeFilter(
            filter.getOrganizationId() != null ? termFilter("organizationId", filter.getOrganizationId()) : null,
            filter.getApplicationId() != null ? termFilter("applicationId", filter.getApplicationId()) : null,
            filter.getProjectId() != null ? termFilter("projectId", filter.getProjectId()) : null,
            filter.getStatus() != null ? termFilter("status", filter.getStatus().name()) : null,
            filter.getStartedSince() != null ? startedSinceFilter(filter.getStartedSince()) : null)));
    }

    private static Query startedSinceFilter(Date since) {
        String from = since.toInstant().toString();
        return Query.of(q -> q.range(r -> r.date(d -> d.field("started").gte(from))));
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
