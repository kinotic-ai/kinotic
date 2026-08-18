import { existsSync } from 'node:fs'
import { spawnSync } from 'node:child_process'

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

/** The chain Docker consults from FORWARD, which is where guest traffic is filtered. */
const CHAIN = 'DOCKER-USER'

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
        return existsSync(DEFAULT_DENY_MARKER) && this.hasIptables()
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
        }

        // Replaced rather than added to, so re-applying cannot accumulate duplicates
        this.release(workloadId)

        const comment = ['-m', 'comment', '--comment', `${RULE_COMMENT_PREFIX}${workloadId}`]
        if (this.resolver !== null) {
            this.run(['-I', CHAIN, '-s', address, '-d', this.resolver,
                      '-p', 'udp', '--dport', '53', ...comment, '-j', 'ACCEPT'])
        }
        for (const destination of allowedHosts) {
            this.run(['-I', CHAIN, '-s', address, '-d', destination, ...comment, '-j', 'ACCEPT'])
        }
    }

    /**
     * Removes the rules belonging to a workload. Does nothing when it has none, so it is safe
     * to call for a workload that never started.
     */
    public release(workloadId: string): void {
        for (const rule of this.rulesFor(`${RULE_COMMENT_PREFIX}${workloadId}`)) {
            // -S prints rules as the -A that would create them; the same words delete it
            this.run(['-D', ...rule.slice(1)])
        }
    }

    /**
     * Drops the rules of every workload not in the given set. A vm-manager that crashed left
     * rules behind for workloads that no longer exist, and Docker reuses their addresses — so
     * without this a new workload can inherit a dead one's access.
     */
    public reconcile(activeWorkloadIds: Set<string>): void {
        const stale = new Set<string>()
        for (const rule of this.rulesFor(RULE_COMMENT_PREFIX)) {
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

    // Rules in the chain whose comment starts with the given text, as argument arrays
    private rulesFor(commentPrefix: string): string[][] {
        const result = spawnSync('iptables', ['-S', CHAIN], { encoding: 'utf-8' })
        let ret: string[][] = []
        if (result.status === 0) {
            ret = (result.stdout ?? '').split('\n')
                .filter(line => line.startsWith(`-A ${CHAIN}`) && line.includes(`--comment "${commentPrefix}`))
                .map(line => this.tokenize(line))
        }
        return ret
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
