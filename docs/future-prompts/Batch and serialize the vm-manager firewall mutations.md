`EgressPolicyManager` on a CLOUD_HYPERVISOR node writes its rules one child process at a time,
synchronously, from the vm-manager's event loop:

```ts
// kinotic-js/workspace/packages/vm-manager/src/internal/api/network/EgressPolicyManager.ts
private run(args: string[]): void {
    const result = spawnSync('iptables', args, { encoding: 'utf-8' })
    ...
}
private ipset(args: string[]): void {
    const result = spawnSync('ipset', args, { encoding: 'utf-8' })
    ...
}
```

`apply` for a workload with two names and two addresses is six rules and seventeen child
processes, about 60 ms of blocked loop; `release` of the same six is about 200 ms, since
`iptables -D` costs 17 to 25 ms each under iptables-nft; `reconcile` at start with fifty
stale rules is about a second. Measured in a network namespace against the backend the nodes
run. `MountQuotaManager` blocks the same way on the start path and is left alone: the quota
calls sit where blocking is harmless.

I want the firewall writes batched, off the loop, serialized, and drained at shutdown.

What I already know, so you don't re-derive it:

- `spawnSync` is what serializes two concurrent workload starts today. Going async without
  a queue lets one `apply` compute a floor position (`floorPosition`) from a listing another
  `apply` just shifted, so the queue is part of the change, not an optimization.
- `iptables-restore --noflush` applies a batch as one kernel transaction and takes the exact
  rule lines the manager already builds, `-I CHAIN N ...` positions included; six inserts or
  six deletes cost about 11 ms in one process. `ipset restore -exist` buffers its input to
  end of file, so creates and pins are one process per batch too.
- A batch aborts whole on one failing line. That is the right behaviour for `apply`, whose
  rules should appear together or not at all. It is the wrong behaviour for `release`, whose
  `-D` lines come from a listing, and Docker edits `DOCKER-USER` too: a rule gone between the
  listing and the restore must not leave the rest of the workload's rules standing.
- Workloads survive a vm-manager restart (`live-restore`), so an `apply` dropped by
  `process.exit` leaves a running VM with no rules until something re-applies it. Draining
  the queue before disconnecting is the correctness gain of this change; the latency is the
  means. `shutdown()` is in `index.ts`.
- The provider's call sites (`CloudHypervisorProvider.ts`, the `apply`, `release` and
  `reconcile` calls) are already inside async methods and gain an `await`.
- p-queue at `concurrency: 1` keeps order, isolates a failing task and drains with
  `onIdle()`; it is ESM-only with two small pure-JS dependencies and Bun loads it. A
  hand-rolled promise chain is a wash except for `onIdle` handling work enqueued mid-wait,
  which is the part not worth owning.

Do this:

1. Build each of `apply`, `release` and `reconcile` as one `iptables-restore --noflush`
   batch and, where sets are involved, one `ipset restore -exist` batch, from the rule lines
   the manager already composes. `release` tolerates a rule that is already gone.
2. Run the children with `Bun.spawn` and `await proc.exited`; `apply`, `release` and
   `reconcile` return `Promise<void>`, and `onIdle(): Promise<void>` is added.
3. Serialize every mutation through one p-queue at concurrency 1, so a listing and the batch
   computed from it are never interleaved with another workload's.
4. `shutdown()` awaits `egress.onIdle()` before anything else it does.
5. The namespace simulation that measured the costs above is the verification: the same
   `apply`, `release`, `reconcile` against real iptables and ipset, with timing, plus the
   unit tests in `test/EgressPolicyManager.test.ts` and `verify-node.sh` on a node.

Keep it to the egress manager and its callers; the quota manager, the resolver, and the
shape of the sets and rules do not change.
