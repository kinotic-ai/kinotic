package org.kinotic.domain.api.model.grind;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.core.api.crud.Identifiable;

import java.util.Date;

/**
 * The persistent record of one execution of a grind pipeline.
 * The individual steps executed during the run are recorded as {@link TaskRecord}s
 * referencing this run's id.
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
     * The registered name of the pipeline this run executed.
     */
    private String pipeline;

    /**
     * The version of the pipeline this run executed.
     */
    private String pipelineVersion;

    /**
     * The description of the executed {@code JobDefinition}.
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
     * When the run started executing.
     */
    private Date started;

    /**
     * When the run reached a terminal status.
     */
    private Date finished;

}
