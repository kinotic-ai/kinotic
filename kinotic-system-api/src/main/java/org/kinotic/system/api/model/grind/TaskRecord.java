package org.kinotic.system.api.model.grind;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.core.api.crud.Identifiable;
import tools.jackson.databind.JsonNode;

import java.util.Date;

/**
 * The persistent record of one step execution within a {@link JobRun}.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class TaskRecord implements Identifiable<String> {

    /**
     * Unique identifier for this record, composed of the {@link #jobRunId} and the {@link #stepPath},
     * so a step has exactly one record per run.
     */
    private String id;

    /**
     * The id of the {@link JobRun} this record belongs to.
     */
    private String jobRunId;

    /**
     * The position of the step within the run's step tree, as the {@code /} separated
     * sequence numbers from the root {@code JobDefinition} down to the step.
     */
    private String stepPath;

    /**
     * The description of the executed {@code Task} or {@code JobDefinition}.
     */
    private String description;

    /**
     * Current status of the step.
     */
    private ExecutionStatus status = ExecutionStatus.RUNNING;

    /**
     * How the step's result was stored in the job scope.
     * Null until the step completes.
     */
    private StoreType storeType;

    /**
     * True if the step's {@code Task} returned further steps that were executed dynamically.
     * A resumed run re-executes such a step so the dynamic steps are regenerated.
     */
    private boolean dynamicSteps;

    /**
     * The name the step's result was stored under in the job scope,
     * or null if the result was not stored.
     */
    private String resultName;

    /**
     * The Java type of the stored result, used to deserialize {@link #resultValue}.
     * Only set when {@link #storeType} is {@link StoreType#STATE}.
     */
    private String resultValueType;

    /**
     * The stored result serialized as JSON.
     * Only set when {@link #storeType} is {@link StoreType#STATE}.
     */
    private JsonNode resultValue;

    /**
     * Output produced by the step while executing, such as command output.
     */
    private String output;

    /**
     * The failure message when {@link #status} is {@link ExecutionStatus#FAILED}.
     */
    private String error;

    /**
     * When the step started executing.
     */
    private Date started;

    /**
     * When the step reached a terminal status.
     */
    private Date finished;

}
