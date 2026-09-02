import { createHash } from 'node:crypto'
import { existsSync, readFileSync, renameSync, rmSync, writeFileSync } from 'node:fs'
import { spawnSync } from 'node:child_process'
import { basename, dirname, join } from 'node:path'

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
 * Prefix of the ipsets the node's resolver fills, one per allowed hostname and shared by every
 * workload allowed that name. The rest of a set's name is a digest of the hostname, since a
 * set name is capped at 31 characters and many hostnames are longer.
 */
const SET_NAME_PREFIX = 'kinotic-'

/**
 * The dnsmasq directives naming the set each allowed hostname's answers go into. Generated
 * here in full and read by dnsmasq only when it starts, so a change is followed by a restart.
 */
const RESOLVER_DIRECTIVES = '/etc/dnsmasq.d/kinotic-egress.conf'

/** The node's dnsmasq unit, restarted when its directives change. */
const RESOLVER_SERVICE = 'dnsmasq'

/**
 * How long an address stays permitted after dnsmasq last answered a workload's lookup with it.
 * Every answer refreshes the entry, and a connection opened while it holds is carried by
 * conntrack afterwards, so this only has to outlast the gap between a guest resolving a name
 * and connecting to what it got.
 */
const SET_ENTRY_TIMEOUT_SECONDS = 300

// IPv4 address or CIDR
const ADDRESS_OR_CIDR = /^(\d{1,3}\.){3}\d{1,3}(\/([0-9]|[12][0-9]|3[0-2]))?$/

// A hostname as RFC 1123 has it: labels of letters, digits and inner hyphens, the last one
// holding a letter so that a malformed address is not taken for a name
const HOSTNAME = /^(?=.{1,253}$)([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\.)*(?=[a-z0-9-]*[a-z])[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$/i

/**
 * Grants a workload's micro VM access to the destinations its policy allows, and nothing else.
 *
 * The node's firewall denies workload egress by default, appended below this chain's rules;
 * everything written here is an exception inserted above it. A workload whose rules were never
 * applied therefore reaches nothing, rather than reaching everything — which is what makes a
 * provider that dies mid-start a failed workload rather than an open one.
 *
 * An address or CIDR becomes a rule naming it. A hostname becomes a rule matching an ipset that
 * the node's dnsmasq fills with every address it answers for that name — the guest is given
 * dnsmasq as its only resolver, so an address it connects to by name is one dnsmasq just
 * answered, however the name's records move. A name covers its subdomains, as dnsmasq matches
 * them. The set is shared by every workload allowed the name and lives until {@link reconcile}
 * finds no rule matching it, so a name that keeps being allowed never has dnsmasq restarted.
 *
 * Rules are matched back to their workload by an iptables comment, so nothing is persisted
 * here and {@link reconcile} can drop whatever a previous process left behind.
 *
 * Requires iptables and root; hostnames also need ipset and a running dnsmasq reading
 * /etc/dnsmasq.d. {@link enforces} reports whether this node denies by default.
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
        return existsSync(DEFAULT_DENY_MARKER) && this.hasCommand('iptables')
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
     * Throws for a destination that is neither an address, a CIDR nor a hostname, and for a
     * hostname on a node without the dnsmasq and ipset that pin one to addresses — a name that
     * cannot be enforced is refused rather than dropped from the policy.
     *
     * @param workloadId the workload the rules belong to
     * @param address the micro VM's address on the workload bridge
     * @param allowedHosts destinations from the workload's network policy, as IPv4 addresses,
     *        CIDRs, or hostnames. The api-gateway is among them: the server places it there,
     *        because only the server knows where the gateway is.
     */
    public apply(workloadId: string, address: string, allowedHosts: string[]): void {
        if (!ADDRESS_OR_CIDR.test(address)) {
            throw new Error(`workload ${workloadId} is at '${address}', which is not an IPv4 address`)
        }
        const names: string[] = []
        const destinations: string[] = []
        for (const host of allowedHosts) {
            if (ADDRESS_OR_CIDR.test(host)) {
                destinations.push(host)
                const granted = this.protectedAddressNamedBy(host)
                if (granted !== null) {
                    // Only the server can reach the service that carries a workload's policy, so
                    // an address named outright is a decision it made; it is still worth a record
                    console.warn(`Workload ${workloadId} is granted ${granted}, where this host hands `
                                 + 'out its own identity')
                }
            } else if (HOSTNAME.test(host)) {
                names.push(host.toLowerCase())
            } else {
                throw new Error(`an allowed destination of workload ${workloadId} is '${host}', which is `
                                + 'neither an IPv4 address or CIDR nor a hostname')
            }
        }
        if (names.length > 0 && !this.resolvesNames()) {
            throw new Error(`Workload ${workloadId} is allowed '${names[0]}' by name, but this node has no `
                            + 'running dnsmasq and ipset to pin a name to the addresses it resolves to, '
                            + 'so the name cannot be enforced')
        }

        // Replaced rather than added to, so re-applying cannot accumulate duplicates
        this.release(workloadId)
        // Before the rules, so dnsmasq is filling a set by the time a rule matches it
        if (names.length > 0) {
            this.syncResolver(names)
        }

        const comment = ['-m', 'comment', '--comment', `${RULE_COMMENT_PREFIX}${workloadId}`]
        if (this.resolver !== null) {
            this.insert(this.floorPosition(), ['-s', address, '-d', this.resolver,
                                               '-p', 'udp', '--dport', '53', ...comment, '-j', 'ACCEPT'])
        }
        for (const name of names) {
            this.insert(this.floorPosition(), ['-s', address, '-m', 'set', '--match-set', this.setOf(name), 'dst',
                                               ...comment, '-j', 'ACCEPT'])
        }
        if (names.length > 0) {
            // A set entry expires while a connection opened through it may still be running,
            // so a connection already admitted is carried by conntrack rather than re-matched
            // per packet. An address rule never expires, and needs no such carry-over.
            this.insert(this.floorPosition(), ['-s', address, '-m', 'conntrack', '--ctstate', 'ESTABLISHED,RELATED',
                                               ...comment, '-j', 'ACCEPT'])
        }
        for (const destination of destinations) {
            // A destination that names a protected address goes above the node's own drops so it
            // can override them; everything else goes below, which is what lets a policy of
            // 0.0.0.0/0 mean the whole internet without also meaning the host's identity
            const position = this.protectedAddressNamedBy(destination) !== null ? 1 : this.floorPosition()
            this.insert(position, ['-s', address, '-d', destination, ...comment, '-j', 'ACCEPT'])
        }
    }

    /**
     * Where a rule goes to sit below the node's own drops and above its default-deny. Falls at
     * the top when the node has no default-deny, since nothing is being overridden there.
     */
    private floorPosition(): number {
        const rules = this.chain()
        // The default-deny is the node's only rule with a source and no destination that drops;
        // its own metadata drops name a destination, and every per-workload rule accepts
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
        for (const rule of this.chain().filter(rule => this.workloadIdOf(rule) === workloadId)) {
            // -S prints rules as the -A that would create them; the same words delete it
            this.run(['-D', ...rule.slice(1)])
        }
    }

    /**
     * Drops the rules of every workload not in the given set, then every resolver directive
     * and ipset no remaining rule matches. A vm-manager that crashed left rules behind for
     * workloads that no longer exist, and Docker reuses their addresses — so without this a
     * new workload can inherit a dead one's access.
     */
    public reconcile(activeWorkloadIds: Set<string>): void {
        const stale = new Set<string>()
        for (const rule of this.chain()) {
            const workloadId = this.workloadIdOf(rule)
            if (workloadId !== null && !activeWorkloadIds.has(workloadId)) {
                stale.add(workloadId)
            }
        }
        for (const workloadId of stale) {
            console.log(`Dropping egress rules left by workload ${workloadId}`)
            this.release(workloadId)
        }
        this.syncResolver([])
    }

    /**
     * Brings dnsmasq's directives and the kernel's sets to the hostnames some rule still
     * matches plus the given ones. dnsmasq reads directives only at startup, so it is
     * restarted when they change and only then: each restart briefly leaves every guest on
     * the node without a resolver. Releasing a workload therefore leaves its names in place —
     * a set no rule matches grants nothing — and a deployment's sync workload, allowed the
     * same names on every run, restarts dnsmasq once rather than twice per run.
     */
    private syncResolver(names: string[]): void {
        const matched = new Set(this.chain().flatMap(rule => this.setsMatchedBy(rule)))
        const domains = new Set([
            ...this.configuredDomains().filter(domain => matched.has(this.setOf(domain))),
            ...names,
        ])
        for (const domain of domains) {
            this.ipset(['create', this.setOf(domain), 'hash:ip', 'family', 'inet',
                        'timeout', String(SET_ENTRY_TIMEOUT_SECONDS), '-exist'])
        }

        const directives = this.directivesFor(domains)
        if (directives !== this.currentDirectives()) {
            this.writeDirectives(directives)
            this.restartResolver()
        }

        const kept = new Set([...domains].map(domain => this.setOf(domain)))
        for (const set of this.sets()) {
            if (!kept.has(set) && !matched.has(set)) {
                this.ipset(['destroy', set])
            }
        }
    }

    // One directive per hostname, listing the set of every configured name it falls under.
    // dnsmasq feeds an answer to the single longest-matching directive, so an answer for
    // api.github.com reaches the set of a workload allowed github.com only through the
    // directive for api.github.com.
    private directivesFor(domains: Set<string>): string {
        const sorted = [...domains].sort()
        const lines = sorted.map(domain => {
            const sets = sorted.filter(other => domain === other || domain.endsWith(`.${other}`))
                               .map(other => this.setOf(other))
            return `ipset=/${domain}/${sets.join(',')}`
        })
        return lines.length > 0
            ? '# Written by the kinotic vm-manager: the ipset each allowed hostname resolves into.\n'
              + '# Regenerated whenever a workload is allowed a name missing here; edits do not survive.\n'
              + `${lines.join('\n')}\n`
            : ''
    }

    private configuredDomains(): string[] {
        return this.currentDirectives().split('\n')
            .map(line => /^ipset=\/([^/]+)\//.exec(line)?.[1])
            .filter((domain): domain is string => domain !== undefined)
    }

    private currentDirectives(): string {
        return existsSync(RESOLVER_DIRECTIVES) ? readFileSync(RESOLVER_DIRECTIVES, 'utf-8') : ''
    }

    // Written whole then renamed, so dnsmasq can never start on a half-written file. The dot
    // prefix keeps dnsmasq from reading the temporary file, which its conf-dir skips.
    private writeDirectives(directives: string): void {
        if (directives === '') {
            rmSync(RESOLVER_DIRECTIVES, { force: true })
        } else {
            const temporary = join(dirname(RESOLVER_DIRECTIVES), `.${basename(RESOLVER_DIRECTIVES)}.tmp`)
            writeFileSync(temporary, directives)
            renameSync(temporary, RESOLVER_DIRECTIVES)
        }
    }

    private restartResolver(): void {
        const result = spawnSync('systemctl', ['restart', RESOLVER_SERVICE], { encoding: 'utf-8' })
        if (result.status !== 0) {
            const output = `${result.stdout ?? ''}${result.stderr ?? ''}`.trim()
            throw new Error(`restarting ${RESOLVER_SERVICE} failed: ${output || `exit ${result.status}`}`)
        }
    }

    // Whether hostnames can be pinned to addresses on this node: dnsmasq must be the running
    // resolver for its answers to be what a guest connects to, and ipset must exist to hold them
    private resolvesNames(): boolean {
        return this.hasCommand('ipset')
            && spawnSync('systemctl', ['is-active', '--quiet', RESOLVER_SERVICE], { encoding: 'utf-8' }).status === 0
    }

    private setOf(domain: string): string {
        return `${SET_NAME_PREFIX}${createHash('sha256').update(domain).digest('hex').slice(0, 16)}`
    }

    // Every set this manager created, read back from the kernel; none where ipset is absent
    private sets(): string[] {
        const result = spawnSync('ipset', ['list', '-n'], { encoding: 'utf-8' })
        let ret: string[] = []
        if (result.status === 0) {
            ret = (result.stdout ?? '').split('\n').filter(name => name.startsWith(SET_NAME_PREFIX))
        }
        return ret
    }

    private setsMatchedBy(rule: string[]): string[] {
        const index = rule.indexOf('--match-set')
        return index >= 0 && rule[index + 1] !== undefined ? [rule[index + 1]!] : []
    }

    // Every rule in the chain, in order, as argument arrays
    private chain(): string[][] {
        const result = spawnSync('iptables', ['-S', CHAIN], { encoding: 'utf-8' })
        let ret: string[][] = []
        if (result.status === 0) {
            ret = (result.stdout ?? '').split('\n')
                .filter(line => line.startsWith(`-A ${CHAIN}`))
                .map(line => this.tokenize(line))
        }
        return ret
    }

    private insert(position: number, rule: string[]): void {
        this.run(['-I', CHAIN, String(position), ...rule])
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

    private hasCommand(command: string): boolean {
        return spawnSync('sh', ['-c', `command -v ${command}`], { encoding: 'utf-8' }).status === 0
    }

    private run(args: string[]): void {
        const result = spawnSync('iptables', args, { encoding: 'utf-8' })
        if (result.status !== 0) {
            const output = `${result.stdout ?? ''}${result.stderr ?? ''}`.trim()
            throw new Error(`iptables ${args.join(' ')} failed: ${output || `exit ${result.status}`}`)
        }
    }

    private ipset(args: string[]): void {
        const result = spawnSync('ipset', args, { encoding: 'utf-8' })
        if (result.status !== 0) {
            const output = `${result.stdout ?? ''}${result.stderr ?? ''}`.trim()
            throw new Error(`ipset ${args.join(' ')} failed: ${output || `exit ${result.status}`}`)
        }
    }

}
