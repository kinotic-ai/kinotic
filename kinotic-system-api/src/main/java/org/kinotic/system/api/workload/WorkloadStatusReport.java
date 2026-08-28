package org.kinotic.system.api.workload;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.system.api.model.workload.WorkloadStatus;

/**
 * A vm-manager node's view of one workload's status, reported to the orchestrator.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class WorkloadStatusReport {

    /**
     * Id of the workload the report is about.
     */
    private String workloadId;

    /**
     * The workload's status on the node.
     */
    private WorkloadStatus status;

    /**
     * The exit status of the workload's process once its run has ended, null while it has not.
     */
    private Integer exitCode;

    /**
     * When the node recorded this status, in epoch milliseconds.
     */
    private long updated;
}
