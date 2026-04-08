package org.kinotic.os.api.model.workload;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.core.api.crud.Identifiable;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents a workload to be managed by a VmManager on a specific node.
 * A workload defines the configuration for a micro VM instance.
 */
@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
public class Workload implements Identifiable<String> {

    /**
     * Unique identifier for this workload.
     */
    private String id;

    /**
     * Human-readable name for the workload.
     */
    private String name;

    /**
     * Optional description of the workload.
     */
    private String description;

    /**
     * The id of the {@link VmNode} this workload is deployed on.
     */
    private String nodeId;

    /**
     * The VM provider to use for this workload.
     */
    private VmProviderType providerType = VmProviderType.BOXLITE;

    /**
     * The image or rootfs to use for the VM.
     */
    private String image;

    /**
     * Number of vCPUs allocated to the VM.
     */
    private int vcpus = 1;

    /**
     * Memory allocated to the VM in megabytes.
     */
    private int memoryMb = 512;

    /**
     * Disk size allocated to the VM in megabytes.
     */
    private int diskSizeMb = 1024;

    /**
     * Current status of the workload.
     */
    private WorkloadStatus status = WorkloadStatus.PENDING;

    /**
     * Optional environment variables to pass to the VM.
     */
    private Map<String, String> environment = new HashMap<>();

    /**
     * Optional port mappings from host to guest (hostPort -> guestPort).
     */
    private Map<Integer, Integer> portMappings = new HashMap<>();

    /**
     * The date and time the workload was created.
     */
    private Date created;

    /**
     * The date and time the workload was last updated.
     */
    private Date updated;

    public Workload(String name, String image) {
        this.name = name;
        this.image = image;
    }
}
