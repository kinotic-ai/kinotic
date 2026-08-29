import Docker from 'dockerode'
import { spawnSync } from 'node:child_process'

/**
 * Image the anchor runs. It only has to hold a network namespace open, so the smallest image
 * that can sleep is enough.
 */
const ANCHOR_IMAGE = 'alpine:latest'

/** The anchor must be an ordinary container that owns a real namespace, not another micro VM. */
const ANCHOR_RUNTIME = 'runc'

/** Longest sleep busybox accepts, so the anchor outlives every workload attached to it. */
const ANCHOR_COMMAND = ['sleep', '2147483647']

/** Says what the container is to anyone reading `docker ps`; the name is what looks it up. */
const ANCHOR_LABEL = 'ai.kinotic.netns-anchor'

/** Interface Docker gives the anchor, and the one Kata attaches its ingress filter to. */
const ANCHOR_INTERFACE = 'eth0'

/** Tap device Kata creates in the namespace for the guest's side of the pair. */
const KATA_TAP = 'tap0_kata'

/**
 * Holds a network namespace open for a workload, so the namespace is already populated when
 * the workload's micro VM boots.
 *
 * Kata attaches a NIC one of two ways: it folds the device into the VM's initial configuration
 * when the namespace already has an interface, and otherwise hot-plugs it once the VM is up.
 * Docker populates a container's namespace only after the VMM exists, so the hot-plug path is
 * the one it takes — and a guest that cannot observe a hot-plug never sees the NIC. Attaching
 * the workload to a namespace an ordinary container already owns puts the interface there
 * first, which is the arrangement Kata is designed around and CRI produces with its pod
 * sandbox.
 *
 * The anchor owns the workload's published ports and resolver, because it owns the namespace
 * they belong to. It outlives the workload so a stopped workload can be started again into the
 * same namespace, keeping its address and its writable layer.
 */
export class NetnsAnchorManager {

    private readonly docker: Docker
    private readonly resolver: string | null

    /**
     * @param docker client for the daemon the workloads run on
     * @param resolver DNS server to give the namespace, or null to leave the daemon's choice
     */
    constructor(docker: Docker, resolver: string | null = null) {
        this.docker = docker
        this.resolver = resolver
    }

    /**
     * Returns the id of the namespace a workload should attach to, creating and starting the
     * anchor if it does not have one yet, and clearing whatever a previous workload left in it.
     *
     * @param workloadId the workload the namespace belongs to
     * @param exposedPorts ports the workload listens on, in Docker's create format
     * @param portBindings host bindings for those ports, in Docker's create format
     */
    async ensure(workloadId: string,
                 exposedPorts: Record<string, Record<string, never>>,
                 portBindings: Record<string, Array<{ HostIp?: string, HostPort?: string }>>): Promise<string> {
        const name = this.anchorName(workloadId)
        let id = await this.runningAnchorId(name)
        if (id === null) {
            await this.remove(name)
            const anchor = await this.docker.createContainer({
                name,
                Image: ANCHOR_IMAGE,
                Cmd: ANCHOR_COMMAND,
                Labels: { [ANCHOR_LABEL]: workloadId },
                ExposedPorts: exposedPorts,
                HostConfig: {
                    Runtime: ANCHOR_RUNTIME,
                    NetworkMode: 'bridge',
                    PortBindings: portBindings,
                    ...(this.resolver !== null ? { Dns: [this.resolver] } : {}),
                    // The vm-manager owns the anchor's lifecycle the same way it owns the
                    // workload's, so the daemon must not bring it back on its own
                    RestartPolicy: { Name: 'no' },
                },
            })
            await anchor.start()
            id = (await anchor.inspect()).Id
        }
        this.clean(workloadId)
        return id
    }

    /**
     * Removes what Kata leaves behind in a workload's namespace, so the next micro VM can
     * attach to it again.
     *
     * Kata's teardown does not detach its endpoint from a namespace it did not create, leaving
     * the tap device and the ingress qdisc it added to the interface. The next start then fails
     * on one of them — `unsupported link type: tuntap`, or `add virt ingress: File exists`.
     */
    clean(workloadId: string): void {
        const pid = this.anchorPid(workloadId)
        if (pid === null) {
            return
        }
        this.inNamespace(pid, ['ip', 'link', 'del', KATA_TAP])
        this.inNamespace(pid, ['tc', 'qdisc', 'del', 'dev', ANCHOR_INTERFACE, 'ingress'])
    }

    /**
     * The address on the workload bridge that a workload's traffic carries, or null when it has
     * no anchor. The workload's own container has none — it joined the anchor's namespace, so
     * the address belongs to the anchor and that is what firewall rules must match on.
     */
    async addressOf(workloadId: string): Promise<string | null> {
        let ret: string | null = null
        try {
            const info = await this.docker.getContainer(this.anchorName(workloadId)).inspect()
            ret = info.NetworkSettings?.Networks?.bridge?.IPAddress || null
        } catch {
            // No anchor, which a workload that asked for no network legitimately has
        }
        return ret
    }

    /** Discards the namespace a workload was using, once the workload itself is gone. */
    async release(workloadId: string): Promise<void> {
        await this.remove(this.anchorName(workloadId))
    }

    /** Name is the lookup key, so the anchor of a workload is found without persisting anything. */
    private anchorName(workloadId: string): string {
        return `${workloadId}-netns`
    }

    private async runningAnchorId(name: string): Promise<string | null> {
        let ret: string | null = null
        try {
            const info = await this.docker.getContainer(name).inspect()
            if (info.State.Running) {
                ret = info.Id
            }
        } catch {
            // No anchor for this workload yet, which ensure() answers by creating one
        }
        return ret
    }

    private anchorPid(workloadId: string): number | null {
        let ret: number | null = null
        try {
            // Synchronous because clean() runs between a stop and the start that depends on it
            const result = spawnSync('docker',
                                     ['inspect', '-f', '{{.State.Pid}}', this.anchorName(workloadId)],
                                     { encoding: 'utf-8' })
            const pid = Number((result.stdout ?? '').trim())
            if (result.status === 0 && Number.isInteger(pid) && pid > 0) {
                ret = pid
            }
        } catch {
            // Treated as no anchor: clean() has nothing to do and the caller's start reports it
        }
        return ret
    }

    // Both deletes are expected to fail when the workload never started, so the status is not
    // checked — what matters is that neither is present afterwards
    private inNamespace(pid: number, command: string[]): void {
        spawnSync('nsenter', ['-t', String(pid), '-n', ...command], { encoding: 'utf-8' })
    }

    private async remove(name: string): Promise<void> {
        try {
            await this.docker.getContainer(name).remove({ force: true })
        } catch {
            // Already gone, which is the state release() is asking for
        }
    }
}
