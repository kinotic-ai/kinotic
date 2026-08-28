package org.kinotic.grindv2.api.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.core.api.crud.Identifiable;
import tools.jackson.databind.JsonNode;

import java.util.Date;

/**
 * The persistent record of one task's execution within a {@link JobRun} - a {@code Task} or a
 * nested {@link JobDefinition} - identified by its {@link #taskPath}, the task's position in
 * the run's task tree.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class TaskRecord implements Identifiable<String> {

    /**
     * Unique identifier for this record, composed of the {@link #jobRunId} and the
     * {@link #taskPath}, so a task has exactly one record per run.
     */
    private String id;

    /**
     * The id of the {@link JobRun} this record belongs to.
     */
    private String jobRunId;

    /**
     * The position of the task within the run's task tree, as the {@code /} separated
     * sequence numbers from the root {@link JobDefinition} down to the task.
     */
    private String taskPath;

    /**
     * The description of the executed task.
     */
    private String description;

    /**
     * Current status of the task.
     */
    private ExecutionStatus status = ExecutionStatus.RUNNING;

    /**
     * How the task's result was stored in the job scope. Null until the task completes.
     */
    private StoreType storeType;

    /**
     * True if the task produced further tasks that were executed dynamically. A resumed run
     * re-executes such a task so the dynamic tasks are regenerated.
     */
    private boolean dynamicTasks;

    /**
     * The name the task's result was stored under in the job scope, or null if the result
     * was not stored.
     */
    private String resultName;

    /**
     * The Java type of the stored result, used to deserialize {@link #resultValue}.
     * Only set when {@link #storeType} is {@link StoreType#STATE}.
     */
    private String resultValueType;

    /**
     * The stored result serialized as JSON. Only set when {@link #storeType} is
     * {@link StoreType#STATE}.
     */
    private JsonNode resultValue;

    /**
     * The stored result serialized as JSON for watchers of the run. Only set when the task's
     * {@link Store} declared wire publication.
     */
    private JsonNode wireValue;

    /**
     * The failure message when {@link #status} is {@link ExecutionStatus#FAILED}.
     */
    private String error;

    /**
     * When the task started executing.
     */
    private Date started;

    /**
     * When the task reached a terminal status.
     */
    private Date finished;

}
