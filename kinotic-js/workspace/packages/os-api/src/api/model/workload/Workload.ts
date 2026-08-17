import type { Identifiable } from '@kinotic-ai/core'
import { WorkloadStatus } from '@/api/model/workload/WorkloadStatus'
import { VmProviderType } from '@/api/model/workload/VmProviderType'
import type { VolumeMount } from '@/api/model/workload/VolumeMount'
import type { PortMapping } from '@/api/model/workload/PortMapping'
import { NetworkPolicy } from '@/api/model/workload/NetworkPolicy'
import { LogPolicy } from '@/api/model/workload/LogPolicy'

/**
 * Represents a workload to be managed by the VM manager.
 * A workload defines the configuration for a micro VM instance.
 */
export class Workload implements Identifiable<string> {

    /**
     * Unique identifier for this workload.
     */
    public id: string | null = null

    /**
     * Human-readable name for the workload.
     */
    public name: string

    /**
     * Optional description of the workload.
     */
    public description?: string

    /**
     * The id of the node this workload is deployed to, assigned by the orchestrator
     * during deployment. Null until the workload has been placed.
     */
    public nodeId: string | null = null

    /**
     * The Organization this workload runs on behalf of, and which may view its logs.
     * Null for platform workloads (SYSTEM scope).
     */
    public organizationId: string | null = null

    /**
     * The Application this workload runs on behalf of, or null if none.
     * When set, organizationId must also be set.
     */
    public applicationId: string | null = null

    /**
     * The VM provider to use for this workload.
     */
    public providerType: VmProviderType = VmProviderType.BOXLITE

    /**
     * The image or rootfs to use for the VM.
     */
    public image: string

    /**
     * Number of vCPUs allocated to the VM.
     */
    public vcpus: number = 1

    /**
     * Memory allocated to the VM in megabytes.
     */
    public memoryMb: number = 512

    /**
     * Disk size allocated to the VM in megabytes.
     */
    public diskSizeMb: number = 1024

    /**
     * The network access granted to this workload's VM.
     */
    public network: NetworkPolicy = new NetworkPolicy()

    /**
     * How much of this workload's log output the node keeps.
     */
    public logPolicy: LogPolicy = new LogPolicy()

    /**
     * When true the VM runs detached from the vm-manager process and survives its
     * restarts. Non-detached workloads end when the vm-manager exits.
     */
    public detached: boolean = true

    /**
     * When true the VM and its disk are discarded when the workload stops, so a
     * stopped workload cannot be restarted. When false the disk is kept and the
     * workload may be restarted in place.
     */
    public autoRemove: boolean = false

    /**
     * Current status of the workload.
     */
    public status: WorkloadStatus = WorkloadStatus.PENDING

    /**
     * The exit status of the workload's process once it has stopped, null while it has not.
     * A workload killed by a signal reports 128 plus the signal number, so 137 is a SIGKILL —
     * which is what a workload that exceeded its memory limit receives, with no chance to
     * shut down first. FAILED alone does not distinguish that from code that threw.
     */
    public exitCode: number | null = null

    /**
     * Optional environment variables to pass to the VM.
     */
    public environment: Record<string, string> = {}

    /**
     * Port mappings that expose guest ports on the host.
     */
    public portMappings: PortMapping[] = []

    /**
     * Volume mounts that expose host directories inside the guest VM.
     */
    public volumeMounts: VolumeMount[] = []

    /**
     * Overrides the image entrypoint. Empty keeps the image default.
     */
    public entrypoint: string[] = []

    /**
     * Overrides the image command (CMD). Empty keeps the image default, unless
     * entrypoint is declared — then the image CMD is dropped so the entrypoint
     * runs exactly as given.
     */
    public cmd: string[] = []

    /**
     * The date and time the workload was created.
     */
    public created: number | null = null

    /**
     * The date and time the workload was last updated.
     */
    public updated: number | null = null

    constructor(name: string, image: string) {
        this.name = name
        this.image = image
    }
}
