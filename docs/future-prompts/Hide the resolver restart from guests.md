On a CLOUD_HYPERVISOR node, the first workload allowed a name dnsmasq has no directive for
restarts dnsmasq, because dnsmasq reads `ipset=` directives only at start:

```ts
// kinotic-js/workspace/packages/vm-manager/src/internal/api/network/EgressPolicyManager.ts
const directives = this.directivesFor(domains)
if (directives !== this.currentDirectives()) {
    this.writeDirectives(directives)
    this.restartResolver()      // tens of milliseconds with no resolver on the node
}
```

For that window the kernel answers a guest's query with ICMP port unreachable, which a
resolver treats as an immediate failure rather than a timeout, so a lookup landing in the gap
fails. The names an environment allows are not known in advance, since new workloads bring
new names, so pre-writing directives is not the answer, and neither is replacing dnsmasq with
a resolver of our own: the battle-tested resolvers that write answers into address sets,
dnsmasq and Unbound, both read the domain list as configuration. I want the restart to be a
delay a guest never notices instead of a failure it does.

What I already know, so you don't re-derive it:

- A dropped query, as opposed to a refused one, makes the guest's resolver wait and retry.
  glibc's defaults are a 5 s timeout and 2 attempts; Docker writes `options` into the guest's
  `resolv.conf` from the container spec's `DnsOptions`, beside the `Dns` the provider already
  sets in `CloudHypervisorProvider`.
- `INPUT` is where the node's floor already admits UDP 53 to the bridge address
  (`kinotic-node-firewall`), so a DROP inserted at position 1 for the restart's duration takes
  precedence and is removed afterwards.
- A connection already open is unaffected: only new lookups wait.

Do this:

1. In `restartResolver`, insert a DROP for UDP 53 to the resolver address at the top of
   `INPUT`, restart dnsmasq, and remove the DROP in a `finally`, so a failed restart never
   leaves the drop behind.
2. Give guests `DnsOptions: ['timeout:1', 'attempts:3']`, so a lookup that lands in the
   window is answered on its first retry about a second later.
3. Extend `test/EgressPolicyManager.reachability.test.ts`: a guest resolving in a loop while
   a new name is allowed sees a slow answer and no failure.

Nothing else changes: the same dnsmasq, sets, rules and directives file.
