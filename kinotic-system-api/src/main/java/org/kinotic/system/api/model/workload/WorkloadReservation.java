package org.kinotic.system.api.model.workload;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.management.api.model.workload.Workload;

/**
 * The room one workload's run holds on a {@link VmNode}: the CPU, memory and disk its VM is sized
 * for, from the moment it is placed until its run ends or it is destroyed. A workload holds at most
 * one reservation per node, keyed by its id, so reserving or releasing the same workload twice
 * changes nothing the second time.
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
     * The reservation a workload's run needs: everything the workload is sized for.
     */
    public static WorkloadReservation forRun(Workload workload) {
        return new WorkloadReservation().setWorkloadId(workload.getId())
                                        .setCpus(workload.getCpus())
                                        .setMemoryMb(workload.getMemoryMb())
                                        .setDiskMb(workload.getDiskSizeMb());
    }
}
