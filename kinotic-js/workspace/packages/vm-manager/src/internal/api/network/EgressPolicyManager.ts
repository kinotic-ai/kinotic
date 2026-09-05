import { existsSync } from 'node:fs'
import { spawnSync } from 'node:child_process'
import type { HostPort } from '@/internal/api/model/HostPort'

/**
 * Marker carried on every rule this manager writes, so the rules belonging to a workload can
 * be found again without persisting anything here. A restarted vm-manager reads them back
 * out of the kernel, the same way {@link MountQuotaManager} reads a project id off an inode.
 */
const RULE_COMMENT_PREFIX = 'kinotic:'

/**
 * Present when the node's firewall denies workload egress by default. Written by the node's
 * provisioning, not by this process — a node either enforces egress or it does not, and that
 * is a property of how it was built.
 */
const DEFAULT_DENY_MARKER = '/etc/kinotic/egress-default-deny'

/**
 * The link-local address a cloud hands instance metadata and identity tokens out on, the same
 * one on Azure, AWS, and GCP. A guest that reaches it can read the host's own credentials.
 */
const CLOUD_METADATA_ADDRESS = '169.254.169.254'

/** Azure's WireServer, whose control ports carry the host agent's goal state and certificates. */
const AZURE_WIRESERVER_ADDRESS = '168.63.129.16'

/**
 * Addresses the node blocks for every workload, being where a host hands out its own identity.
 * Rules written here are inserted above the node's own, so permitting one of these re-opens it
 * for that workload — which only the server may decide, and only by naming the address itself.
 */
const PROTECTED_ADDRESSES = [CLOUD_METADATA_ADDRESS, AZURE_WIRESERVER_ADDRESS]

/** The chain Docker consults from FORWARD, which is where guest traffic is filtered. */
const CHAIN = 'DOCKER-USER'

/**
 * Where traffic addressed to the host itself is filtered, which FORWARD never sees. The
 * node's floor shields its own services from every guest here, so a host port a workload is
 * granted has to be opened here too.
 */
const HOST_CHAIN = 'INPUT'

/** The chains this manager writes to, and reads back its rules from. */
const CHAINS = [CHAIN, HOST_CHAIN]

// IPv4 address or CIDR. Hostnames are rejected rather than resolved: an address resolved once
// at start goes stale the moment the target moves, and the workload would keep a rule naming
// an address someone else now holds.
const ADDRESS_OR_CIDR = /^(\d{1,3}\.){3}\d{1,3}(\/([0-9]|[12][0-9]|3[0-2]))?$/

/**
 * Grants a workload's micro VM access to the destinations its policy allows, and nothing else.
 *
 * The node's firewall denies workload egress by default, appended below this chain's rules;
 * everything written here is an exception inserted above it. A workload whose rules were never
 * applied therefore reaches nothing, rather than reaching everything — which is what makes a
 * provider that dies mid-start a failed workload rather than an open one.
 *
 * Rules are matched back to their workload by an iptables comment, so nothing is persisted
 * here and {@link reconcile} can drop whatever a previous process left behind.
 *
 * Requires iptables and root. {@link enforces} reports whether this node denies by default.
 */
export class EgressPolicyManager {

    private readonly resolver: string | null
    // Resolved once: the binary does not come or go while the process runs, unlike the marker
    private readonly hasIptables: boolean = spawnSync('sh', ['-c', 'command -v iptables'], { encoding: 'utf-8' }).status === 0

    /**
     * @param resolver the DNS server workloads are given, permitted on port 53. A property of
     *        the node's network rather than of any workload's policy, so it is the one
     *        destination this manager permits that the workload did not ask for.
     */
    constructor(resolver: string | null = null) {
        this.resolver = resolver
    }

    /**
     * Whether this node denies workload egress unless a rule permits it. When false, the rules
     * this manager writes grant access that was never withheld, so a workload's policy is not
     * being honoured however carefully it was declared.
     */
    public enforces(): boolean {
        return existsSync(DEFAULT_DENY_MARKER) && this.hasIptables
    }

    /**
     * Whether the node's firewall keeps guests away from the cloud metadata endpoint. Written
     * by the node's provisioning rather than here, and asserted because a flushed chain leaves
     * every workload able to read the host's credentials with nothing to indicate it.
     */
    public blocksCloudMetadata(): boolean {
        return spawnSync('iptables', ['-C', CHAIN, '-d', CLOUD_METADATA_ADDRESS, '-j', 'DROP'],
                         { encoding: 'utf-8' }).status === 0
    }

    /**
     * Permits the given address to reach this workload's allowed destinations, and leaves the
     * node's default-deny to refuse the rest. Re-applying replaces the workload's rules, so a
     * recovered workload converges on the policy it should have.
     *
     * @param workloadId the workload the rules belong to
     * @param address the micro VM's address on the workload bridge
     * @param allowedHosts destinations from the workload's network policy, as IPv4 addresses
     *        or CIDRs. The api-gateway is among them: the server places it there, because only
     *        the server knows where the gateway is.
     * @param hostPort a port of this host the workload is granted, opened to it alone; null
     *        when it is granted none
     */
    public apply(workloadId: string,
                 address: string,
                 allowedHosts: string[],
                 hostPort: HostPort | null = null): void {
        this.requireAddress(address, `workload ${workloadId}`)
        for (const destination of allowedHosts) {
            this.requireAddress(destination, `an allowed destination of workload ${workloadId}`)
            const granted = this.protectedAddressNamedBy(destination)
            if (granted !== null) {
                // Only the server can reach the service that carries a workload's policy, so an
                // address named outright is a decision it made; it is still worth a record
                console.warn(`Workload ${workloadId} is granted ${granted}, where this host hands `
                             + 'out its own identity')
            }
        }

        // Replaced rather than added to, so re-applying cannot accumulate duplicates
        this.release(workloadId)

        const comment = ['-m', 'comment', '--comment', `${RULE_COMMENT_PREFIX}${workloadId}`]
        if (this.resolver !== null) {
            this.insert(this.floorPosition(), ['-s', address, '-d', this.resolver,
                                               '-p', 'udp', '--dport', '53', ...comment, '-j', 'ACCEPT'])
        }
        for (const destination of allowedHosts) {
            // A destination that names a protected address goes above the node's own drops so it
            // can override them; everything else goes below, which is what lets a policy of
            // 0.0.0.0/0 mean the whole internet without also meaning the host's identity
            const position = this.protectedAddressNamedBy(destination) !== null ? 1 : this.floorPosition()
            this.insert(position, ['-s', address, '-d', destination, ...comment, '-j', 'ACCEPT'])
        }
        if (hostPort !== null) {
            // At the top: the floor drops the whole bridge subnet from the host's INPUT, and a
            // rule below that drop is never reached
            this.run(['-I', HOST_CHAIN, '1', '-s', address, '-d', hostPort.address,
                      '-p', 'tcp', '--dport', String(hostPort.port), ...comment, '-j', 'ACCEPT'])
        }
    }

    /**
     * Where a rule goes to sit below the node's own drops and above its default-deny. Falls at
     * the top when the node has no default-deny, since nothing is being overridden there.
     */
    private floorPosition(): number {
        const rules = this.rules().filter(rule => rule[1] === CHAIN)
        // The default-deny is the node's only rule with a source and no destination; its own
        // metadata drops name a destination, and every per-workload rule names both
        const index = rules.findIndex(rule => rule.includes('-s') && !rule.includes('-d') && rule.includes('DROP'))
        return index >= 0 ? index + 1 : 1
    }

    /**
     * Removes the rules belonging to a workload. Does nothing when it has none, so it is safe
     * to call for a workload that never started.
     */
    public release(workloadId: string): void {
        // Compared as a whole id rather than a prefix: 'wl-1' is a prefix of 'wl-10', and
        // releasing one workload must not take a sibling's rules with it
        for (const rule of this.rules().filter(rule => this.workloadIdOf(rule) === workloadId)) {
            this.remove(rule)
        }
    }

    /**
     * Drops the rules of every workload not in the given set. A vm-manager that crashed left
     * rules behind for workloads that no longer exist, and Docker reuses their addresses — so
     * without this a new workload can inherit a dead one's access.
     */
    public reconcile(activeWorkloadIds: Set<string>): void {
        const stale = this.rules().filter(rule => {
            const workloadId = this.workloadIdOf(rule)
            return workloadId !== null && !activeWorkloadIds.has(workloadId)
        })
        for (const workloadId of new Set(stale.map(rule => this.workloadIdOf(rule)))) {
            console.log(`Dropping egress rules left by workload ${workloadId}`)
        }
        stale.forEach(rule => this.remove(rule))
    }

    // Every rule in the chains this manager writes to, in order, as argument arrays; one
    // listing of the whole table costs one process where one per chain would cost two
    private rules(): string[][] {
        const result = spawnSync('iptables', ['-S'], { encoding: 'utf-8' })
        let ret: string[][] = []
        if (result.status === 0) {
            ret = (result.stdout ?? '').split('\n')
                .filter(line => CHAINS.some(chain => line.startsWith(`-A ${chain} `)))
                .map(line => this.tokenize(line))
        }
        return ret
    }

    private insert(position: number, rule: string[]): void {
        this.run(['-I', CHAIN, String(position), ...rule])
    }

    // -S prints rules as the -A that would create them; the same words delete it
    private remove(rule: string[]): void {
        this.run(['-D', ...rule.slice(1)])
    }

    private workloadIdOf(rule: string[]): string | null {
        const index = rule.indexOf('--comment')
        const comment = index >= 0 ? rule[index + 1] : undefined
        return comment?.startsWith(RULE_COMMENT_PREFIX) ? comment.slice(RULE_COMMENT_PREFIX.length) : null
    }

    // iptables -S quotes the comment, which is the only argument that can contain spaces
    private tokenize(line: string): string[] {
        return (line.match(/"[^"]*"|\S+/g) ?? []).map(token =>
            token.startsWith('"') ? token.slice(1, -1) : token)
    }

    // The protected address this destination is, or null when it is something else. A bare
    // address and its /32 are the same grant.
    private protectedAddressNamedBy(destination: string): string | null {
        const named = destination.endsWith('/32') ? destination.slice(0, -3) : destination
        return PROTECTED_ADDRESSES.find(address => address === named) ?? null
    }

    private requireAddress(value: string, subject: string): void {
        if (!ADDRESS_OR_CIDR.test(value)) {
            throw new Error(`${subject} is '${value}', which is not an IPv4 address or CIDR. `
                            + 'Egress rules match addresses, so a hostname cannot be enforced.')
        }
    }

    private run(args: string[]): void {
        const result = spawnSync('iptables', args, { encoding: 'utf-8' })
        if (result.status !== 0) {
            const output = `${result.stdout ?? ''}${result.stderr ?? ''}`.trim()
            throw new Error(`iptables ${args.join(' ')} failed: ${output || `exit ${result.status}`}`)
        }
    }

}
