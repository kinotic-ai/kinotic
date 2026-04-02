package org.kinotic.os.api.model.workload;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.core.api.crud.Identifiable;

import java.util.Date;

/**
 * Represents a node in the cluster that is running a VmManager process
 * and is capable of hosting {@link Workload}s.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class VmNode implements Identifiable<String> {

    /**
     * Unique identifier for this node (typically the Kinotic node id).
     */
    private String id;

    /**
     * Human-readable name for the node.
     */
    private String name;

    /**
     * The hostname or address of the node.
     */
    private String hostname;

    /**
     * Current status of the node.
     */
    private VmNodeStatus status = VmNodeStatus.ONLINE;

    /**
     * Total number of vCPUs available on this node.
     */
    private int totalCpus;

    /**
     * Total memory available on this node in megabytes.
     */
    private int totalMemoryMb;

    /**
     * Total disk space available on this node in megabytes.
     */
    private int totalDiskMb;

    /**
     * Number of vCPUs currently allocated to workloads.
     */
    private int allocatedCpus;

    /**
     * Memory currently allocated to workloads in megabytes.
     */
    private int allocatedMemoryMb;

    /**
     * Disk space currently allocated to workloads in megabytes.
     */
    private int allocatedDiskMb;

    /**
     * The date and time the node was last seen/heartbeat.
     */
    private Date lastSeen;

    /**
     * The date and time the node was registered.
     */
    private Date created;

    /**
     * The date and time the node was last updated.
     */
    private Date updated;

    public VmNode(String id, String name, String hostname) {
        this.id = id;
        this.name = name;
        this.hostname = hostname;
    }

    /**
     * @return the number of vCPUs available (not allocated) on this node
     */
    public int getAvailableCpus() {
        return totalCpus - allocatedCpus;
    }

    /**
     * @return the memory available (not allocated) on this node in megabytes
     */
    public int getAvailableMemoryMb() {
        return totalMemoryMb - allocatedMemoryMb;
    }

    /**
     * @return the disk space available (not allocated) on this node in megabytes
     */
    public int getAvailableDiskMb() {
        return totalDiskMb - allocatedDiskMb;
    }
}
