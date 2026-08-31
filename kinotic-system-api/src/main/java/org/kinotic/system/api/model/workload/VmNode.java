package org.kinotic.system.api.model.workload;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.core.api.crud.Identifiable;
import org.kinotic.management.api.model.workload.Workload;

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
     * Whether the node is fit to receive workloads, and why when it is not.
     */
    private VmNodeStatus status = new VmNodeStatus();

    /**
     * The VM provider this node runs every workload on, determined by how the node was
     * provisioned and reported when it registers.
     */
    private VmProviderType providerType = VmProviderType.BOXLITE;

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
     * Base directory every workload volume mount on this node must live under. Reported by
     * the node at registration; deployment flows compose host paths under it.
     */
    private String workloadDataDir;

    public VmNode(String id, String name, String hostname) {
        this.id = id;
        this.name = name;
        this.hostname = hostname;
    }

    /**
     * @return the number of vCPUs available (not allocated) on this node
     */
    @JsonIgnore
    public int getAvailableCpus() {
        return totalCpus - allocatedCpus;
    }

    /**
     * @return the memory available (not allocated) on this node in megabytes
     */
    @JsonIgnore
    public int getAvailableMemoryMb() {
        return totalMemoryMb - allocatedMemoryMb;
    }

    /**
     * @return the disk space available (not allocated) on this node in megabytes
     */
    @JsonIgnore
    public int getAvailableDiskMb() {
        return totalDiskMb - allocatedDiskMb;
    }
}
