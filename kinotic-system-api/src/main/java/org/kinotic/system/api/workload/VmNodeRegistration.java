package org.kinotic.system.api.workload;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.management.api.model.workload.VmProviderType;

/**
 * DTO sent by a vm-manager process when it registers with the server.
 * Contains only the information the node knows about itself.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class VmNodeRegistration {

    /**
     * Unique identifier for this node.
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
     * The VM provider this node runs every workload on.
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
     * Base directory every workload volume mount on this node must live under.
     */
    private String workloadDataDir;

    public VmNodeRegistration(String id, String name, String hostname) {
        this.id = id;
        this.name = name;
        this.hostname = hostname;
    }
}
