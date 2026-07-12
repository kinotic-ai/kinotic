package org.kinotic.domain.api.model.workload;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.core.api.crud.Identifiable;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
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
     * The Organization this workload runs on behalf of, and which may view its logs.
     * {@code null} for platform workloads (SYSTEM scope).
     */
    private String organizationId;

    /**
     * The Application this workload runs on behalf of, or {@code null} if none.
     * When set, {@link #organizationId} must also be set.
     */
    private String applicationId;

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
     * When {@code true} the VM runs detached from the vm-manager process and survives its
     * restarts. Non-detached workloads end when the vm-manager exits.
     */
    private boolean detached = true;

    /**
     * When {@code true} the VM and its disk are discarded when the workload stops, so a
     * stopped workload cannot be restarted. When {@code false} the disk is kept and the
     * workload may be restarted in place.
     */
    private boolean autoRemove = false;

    /**
     * Current status of the workload.
     */
    private WorkloadStatus status = WorkloadStatus.PENDING;

    /**
     * Optional environment variables to pass to the VM.
     */
    private Map<String, String> environment = new HashMap<>();

    /**
     * Port mappings that expose guest ports on the host.
     */
    private List<PortMapping> portMappings = new ArrayList<>();

    /**
     * Volume mounts that expose host directories inside the guest VM.
     */
    private List<VolumeMount> volumeMounts = new ArrayList<>();

    /**
     * Overrides the image entrypoint. Empty keeps the image default.
     */
    private List<String> entrypoint = new ArrayList<>();

    /**
     * Overrides the image command (CMD). Empty keeps the image default, unless
     * {@link #entrypoint} is declared — then the image CMD is dropped so the entrypoint
     * runs exactly as given.
     */
    private List<String> cmd = new ArrayList<>();

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
