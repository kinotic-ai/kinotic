/**
 * DTO sent by a vm-manager process when it registers with the server.
 * Contains only the information the node knows about itself.
 */
export class VmNodeRegistration {

    /**
     * Unique identifier for this node.
     */
    public id: string

    /**
     * Human-readable name for the node.
     */
    public name: string

    /**
     * The hostname or address of the node.
     */
    public hostname: string

    /**
     * Total number of vCPUs available on this node.
     */
    public totalCpus: number = 0

    /**
     * Total memory available on this node in megabytes.
     */
    public totalMemoryMb: number = 0

    /**
     * Total disk space available on this node in megabytes.
     */
    public totalDiskMb: number = 0

    constructor(id: string, name: string, hostname: string) {
        this.id = id
        this.name = name
        this.hostname = hostname
    }
}
