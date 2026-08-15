package org.kinotic.orchestrator.api.model.grind;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.orchestrator.api.services.JobService;

/**
 * One step lifecycle event of a running job, as delivered by {@link JobService#watch(String)}.
 * Each event names the step it concerns by the same {@link TaskRecord#getStepPath()} the run's
 * records use, so a live event and the record it corresponds to identify the same step.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class JobProgressEvent {

    /**
     * What this event reports about the step.
     */
    private JobProgressEventType type;

    /**
     * The position of the step within the run's step tree, as the {@code /} separated
     * sequence numbers from the root {@code JobDefinition} down to the step.
     */
    private String stepPath;

    /**
     * The description of the step, carried by {@link JobProgressEventType#STEP_STARTED}.
     */
    private String description;

    /**
     * How far along the step is, from 0 to 100, carried by
     * {@link JobProgressEventType#STEP_PROGRESS}.
     */
    private Integer percentageComplete;

    /**
     * The progress message for {@link JobProgressEventType#STEP_PROGRESS}, or the failure for
     * {@link JobProgressEventType#STEP_FAILED}.
     */
    private String message;

}
