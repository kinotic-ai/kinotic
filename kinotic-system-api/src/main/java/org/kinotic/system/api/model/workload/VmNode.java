package org.kinotic.system.api.model.workload;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.domain.api.reconcile.Reconcilable;
import org.kinotic.domain.api.reconcile.ReconcileState;
import org.kinotic.management.api.model.workload.Workload;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Represents a node in the cluster that is running a VmManager process
 * and is capable of hosting {@link Workload}s.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class VmNode implements Reconcilable<VmNodeState> {

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
     * What the node should be, taking workloads, and what it reports it is, with what the platform
     * inferred beside the node's word: that it fell silent, or that a call to it could not be
     * delivered. A node is placeable exactly when this is reconciled.
     */
    private ReconcileState<VmNodeState> state = new ReconcileState<>();

    /**
     * Why the node is not taking workloads, or null when it is. Set from the node's own report of
     * the guarantees it can still make — a data root that stopped enforcing disk limits, or a
     * firewall that stopped hiding host credentials from guests.
     */
    private String healthMessage;

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
     * CPU not allocated to any workload, in cores. What is allocated is
     * {@code totalCpus - freeCpus}, the sum of the {@link #reservations}.
     */
    private double freeCpus;

    /**
     * Memory not allocated to any workload, in megabytes.
     */
    private int freeMemoryMb;

    /**
     * Disk space not allocated to any workload, in megabytes.
     */
    private int freeDiskMb;

    /**
     * The room each workload running on this node holds, one entry per workload. The
     * {@code free*} fields are the totals less what these hold, so a workload's room is
     * reserved and released by its id and never counted twice.
     */
    private List<WorkloadReservation> reservations = new ArrayList<>();

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

}
