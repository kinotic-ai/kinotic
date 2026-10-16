package org.kinotic.management.api.model.workload;

import org.kinotic.management.api.model.TaskViews;

/**
 * A workload whose console log a task shows, as the {@link TaskViews#WORKLOAD_LOG} view. The log is
 * read in the organization that owns the job run.
 *
 * @param workloadId the id of the workload
 */
public record WorkloadLog(String workloadId) {
}
