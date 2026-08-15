package org.kinotic.orchestrator.api.model.grind;

/**
 * What a {@link JobProgressEvent} reports about the step it names.
 */
public enum JobProgressEventType {

    /**
     * The step began executing. {@link JobProgressEvent#getDescription()} carries its description.
     */
    STEP_STARTED,

    /**
     * The step reported how far along it is. {@link JobProgressEvent#getPercentageComplete()} and
     * {@link JobProgressEvent#getMessage()} carry the reported progress.
     */
    STEP_PROGRESS,

    /**
     * The step finished successfully.
     */
    STEP_COMPLETED,

    /**
     * The step terminated with a failure. {@link JobProgressEvent#getMessage()} carries the failure.
     */
    STEP_FAILED

}
