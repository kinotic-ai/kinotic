package org.kinotic.grindv2.api.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.core.api.crud.Identifiable;

import java.util.Date;

/**
 * The persistent record of one execution of a {@link JobDefinition}. The individual steps
 * executed during the run are recorded as {@link StepRecord}s referencing this run's id.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class JobRun implements Identifiable<String> {

    /**
     * Unique identifier for this run.
     */
    private String id;

    /**
     * The name of the {@link JobDefinition} this run executed.
     */
    private String name;

    /**
     * The Organization this run executed on behalf of, or {@code null} for platform runs.
     */
    private String organizationId;

    /**
     * The Application this run executed on behalf of, or {@code null} if none.
     */
    private String applicationId;

    /**
     * The Project this run executed on behalf of, or {@code null} if none.
     */
    private String projectId;

    /**
     * The version of the {@link JobDefinition} this run executed.
     */
    private String version;

    /**
     * The description of the executed {@link JobDefinition}.
     */
    private String description;

    /**
     * Current status of the run.
     */
    private ExecutionStatus status = ExecutionStatus.RUNNING;

    /**
     * The failure message when {@link #status} is {@link ExecutionStatus#FAILED}.
     */
    private String error;

    /**
     * The id of the {@link JobRun} this run resumed, or null if this run was not a resume.
     */
    private String resumedFrom;

    /**
     * When the run started executing.
     */
    private Date started;

    /**
     * When the run reached a terminal status.
     */
    private Date finished;

}
