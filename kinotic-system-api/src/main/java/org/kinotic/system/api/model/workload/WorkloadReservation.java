package org.kinotic.system.api.model.workload;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.management.api.model.workload.Workload;

/**
 * The room one workload holds on a {@link VmNode}: its CPU, memory and disk while its VM runs, and
 * its disk alone once the run has ended, since a VM that is kept for a restart keeps its disk on the
 * node. A workload holds at most one reservation per node, keyed by its id, so reserving or
 * releasing the same workload twice changes nothing the second time.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class WorkloadReservation {

    /**
     * The id of the workload holding the reservation.
     */
    private String workloadId;

    /**
     * CPU the workload's VM is allotted, in cores; a fraction is a share of one core.
     */
    private double cpus;

    /**
     * Memory the workload's VM is allotted, in megabytes.
     */
    private int memoryMb;

    /**
     * Disk the workload's VM is allotted, in megabytes.
     */
    private int diskMb;

    /**
     * True while the workload's run holds its CPU and memory; false once the run has ended and only
     * the disk is still held.
     */
    private boolean running;

    /**
     * The reservation a workload's run needs: everything the workload is sized for.
     */
    public static WorkloadReservation forRun(Workload workload) {
        return new WorkloadReservation().setWorkloadId(workload.getId())
                                        .setCpus(workload.getCpus())
                                        .setMemoryMb(workload.getMemoryMb())
                                        .setDiskMb(workload.getDiskSizeMb())
                                        .setRunning(true);
    }
}
