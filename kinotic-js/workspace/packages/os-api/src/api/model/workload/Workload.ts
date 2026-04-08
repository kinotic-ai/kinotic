import type { Identifiable } from '@kinotic-ai/core'
import { WorkloadStatus } from '@/api/model/workload/WorkloadStatus'
import { VmProviderType } from '@/api/model/workload/VmProviderType'

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
     * Current status of the workload.
     */
    public status: WorkloadStatus = WorkloadStatus.PENDING

    /**
     * Optional environment variables to pass to the VM.
     */
    public environment: Record<string, string> = {}

    /**
     * Optional port mappings from host to guest (hostPort -> guestPort).
     */
    public portMappings: Record<number, number> = {}

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
