import { existsSync } from 'node:fs'
import { networkInterfaces } from 'node:os'
import { spawnSync } from 'node:child_process'
import { Environment } from '@/api/Environment'

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
 * Traffic addressed to the node itself never reaches FORWARD, so nothing in {@link CHAIN} says
 * anything about a workload dialling a service on its own node. The node's firewall refuses it
 * from here instead, and this is the only chain an exception can be written to.
 */
const HOST_CHAIN = 'INPUT'

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
    private readonly environment: Environment

    /**
     * @param resolver the DNS server workloads are given, permitted on port 53. A property of
     *        the node's network rather than of any workload's policy, so it is the one
     *        destination this manager permits that the workload did not ask for.
     * @param environment decides whether a destination naming one of this node's own addresses
     *        is honoured. A production deployment keeps the api-gateway off the nodes that run
     *        workloads, so on {@link Environment.PRODUCTION} such a destination is left refused.
     */
    constructor(resolver: string | null = null,
                environment: Environment = Environment.PRODUCTION) {
        this.resolver = resolver
        this.environment = environment
    }

    /**
     * Whether this node denies workload egress unless a rule permits it. When false, the rules
     * this manager writes grant access that was never withheld, so a workload's policy is not
     * being honoured however carefully it was declared.
     */
    public enforces(): boolean {
        return existsSync(DEFAULT_DENY_MARKER) && this.hasIptables()
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
     */
    public apply(workloadId: string, address: string, allowedHosts: string[]): void {
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
            this.insert(CHAIN, this.floorPosition(), ['-s', address, '-d', this.resolver,
                                                      '-p', 'udp', '--dport', '53', ...comment, '-j', 'ACCEPT'])
        }
        for (const destination of allowedHosts) {
            // A destination that names a protected address goes above the node's own drops so it
            // can override them; everything else goes below, which is what lets a policy of
            // 0.0.0.0/0 mean the whole internet without also meaning the host's identity
            const position = this.protectedAddressNamedBy(destination) !== null ? 1 : this.floorPosition()
            this.insert(CHAIN, position, ['-s', address, '-d', destination, ...comment, '-j', 'ACCEPT'])

            // A colocated service is refused by a different chain, so permitting it takes a
            // second rule. Only an exact address counts: a CIDR that happens to contain the
            // node was written to describe a network, not to ask for the node itself.
            if (this.environment === Environment.DEVELOPMENT && this.isThisNode(destination)) {
                this.insert(HOST_CHAIN, this.hostFloorPosition(),
                            ['-s', address, '-d', destination, ...comment, '-j', 'ACCEPT'])
            }
        }
    }

    /** Whether the destination names an address this node answers on. */
    private isThisNode(destination: string): boolean {
        const named = destination.endsWith('/32') ? destination.slice(0, -3) : destination
        return Object.values(networkInterfaces()).flat()
            .some(details => details !== undefined && details.family === 'IPv4' && details.address === named)
    }

    /**
     * Where a rule goes to sit above the node's drop of the workload bridge. Falls at the top
     * when the node has no such drop, which is where it would go anyway.
     */
    private hostFloorPosition(): number {
        // The drop is the node's only INPUT rule with a source and no destination and no port
        const index = this.chain(HOST_CHAIN).findIndex(
            rule => rule.includes('-s') && !rule.includes('-d') && !rule.includes('--dport') && rule.includes('DROP'))
        return index >= 0 ? index + 1 : 1
    }

    /**
     * Where a rule goes to sit below the node's own drops and above its default-deny. Falls at
     * the top when the node has no default-deny, since nothing is being overridden there.
     */
    private floorPosition(): number {
        const rules = this.chain(CHAIN)
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
        // Both chains, because a workload permitted a service on its own node has a rule in each
        for (const chainName of [CHAIN, HOST_CHAIN]) {
            for (const rule of this.chain(chainName).filter(rule => this.workloadIdOf(rule) === workloadId)) {
                // -S prints rules as the -A that would create them; the same words delete it
                this.run(['-D', ...rule.slice(1)])
            }
        }
    }

    /**
     * Drops the rules of every workload not in the given set. A vm-manager that crashed left
     * rules behind for workloads that no longer exist, and Docker reuses their addresses — so
     * without this a new workload can inherit a dead one's access.
     */
    public reconcile(activeWorkloadIds: Set<string>): void {
        const stale = new Set<string>()
        for (const rule of [...this.chain(CHAIN), ...this.chain(HOST_CHAIN)]) {
            const workloadId = this.workloadIdOf(rule)
            if (workloadId !== null && !activeWorkloadIds.has(workloadId)) {
                stale.add(workloadId)
            }
        }
        for (const workloadId of stale) {
            console.log(`Dropping egress rules left by workload ${workloadId}`)
            this.release(workloadId)
        }
    }

    // Every rule in the chain, in order, as argument arrays
    private chain(name: string): string[][] {
        const result = spawnSync('iptables', ['-S', name], { encoding: 'utf-8' })
        let ret: string[][] = []
        if (result.status === 0) {
            ret = (result.stdout ?? '').split('\n')
                .filter(line => line.startsWith(`-A ${name}`))
                .map(line => this.tokenize(line))
        }
        return ret
    }

    private insert(name: string, position: number, rule: string[]): void {
        this.run(['-I', name, String(position), ...rule])
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

    private hasIptables(): boolean {
        return spawnSync('sh', ['-c', 'command -v iptables'], { encoding: 'utf-8' }).status === 0
    }

    private run(args: string[]): void {
        const result = spawnSync('iptables', args, { encoding: 'utf-8' })
        if (result.status !== 0) {
            const output = `${result.stdout ?? ''}${result.stderr ?? ''}`.trim()
            throw new Error(`iptables ${args.join(' ')} failed: ${output || `exit ${result.status}`}`)
        }
    }

}
