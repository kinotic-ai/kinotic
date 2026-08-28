package org.kinotic.system.api.model.workload;

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
     * The id of the {@link VmNode} this workload is deployed on. Assigned by the
     * orchestrator at placement, or set by the caller before deploying to pin the workload
     * to a specific node.
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
     * The network access granted to this workload's VM.
     */
    private NetworkPolicy network = new NetworkPolicy();

    /**
     * How much of this workload's log output the node keeps.
     */
    private LogPolicy logPolicy = new LogPolicy();

    /**
     * When {@code true} the VM runs detached from the vm-manager process and survives its
     * restarts, and calls that start its run (deploy, restart) complete as soon as it is
     * running. When {@code false} the workload runs in the foreground: it ends when the
     * vm-manager exits, and calls that start its run complete only once the run has ended —
     * {@link WorkloadStatus#STOPPED} or {@link WorkloadStatus#FAILED}, with
     * {@link #getExitCode()} set.
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
     * The exit status of the workload's process once it has stopped, null while it has not.
     * A workload killed by a signal reports 128 plus the signal number, so 137 is a SIGKILL —
     * which is what a workload that exceeded its memory limit receives, with no chance to shut
     * down first. FAILED alone does not distinguish that from code that threw.
     */
    private Integer exitCode;

    /**
     * Optional environment variables to pass to the VM. Persisted verbatim on the workload
     * record — put values that must not be readable there in {@link #secrets} instead.
     */
    private Map<String, String> environment = new HashMap<>();

    /**
     * Optional secret environment variables to pass to the VM, delivered to the guest
     * exactly like {@link #environment} and overriding it on a duplicate key. The persisted
     * workload record holds a masked value for every entry — reading a workload back returns
     * the keys but never the values, which only the node receives.
     */
    private Map<String, String> secrets = new HashMap<>();

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
