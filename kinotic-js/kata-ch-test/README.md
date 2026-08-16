# kata-ch-test

Evaluation harness for [Kata Containers](https://katacontainers.io/) on
[Cloud Hypervisor](https://www.cloudhypervisor.org/), as a candidate replacement for boxlite
behind the vm-manager's `IVmProvider`.

It asks the same questions as [`../boxlite-test`](../boxlite-test/README.md), so the answers
are comparable line for line. Several of them are questions boxlite 0.9.7 answered badly —
three volume mounts cannot boot (finding #9), a disabled network cannot boot (#12), an empty
egress allowlist grants everything (#13), a finished workload's exit code is invisible (#6),
and an entrypoint's stdout goes nowhere (#7).

**Nothing here has been run yet.** Fill the results in when it has.

```bash
sudo ./setup-ubuntu.sh          # installs and verifies the stack, prints resolved versions
sudo bun run src/capability-test.ts
```

Needs Ubuntu 22.04 with nested virtualization, root, and outbound internet. Release versions
are resolved from GitHub at run time rather than pinned, and printed, so a run always reports
exactly what it tested.

---

## Why phase 0 aborts

Kata is selected per container by a runtime name. If that name resolves to a shim that is not
installed, or the config picks QEMU instead of Cloud Hypervisor, containerd will happily run
the workload under `runc` — and every capability below would then pass while providing no VM
isolation at all. Phase 0 compares the guest's kernel against the host's and refuses to
continue if they match, so a green report can never mean "this was a container".

## What each phase decides

| Phase | Question | What it decides for the vm-manager |
|---|---|---|
| 0 | Is each workload really a VM, with a `cloud-hypervisor` process behind it? | Whether any other result can be trusted |
| 1 | entrypoint / cmd / env / workdir | Whether `Workload`'s OCI fields survive the move — the reason boxlite was chosen |
| 2 | 1, 2, 3, 4 volume mounts; is `readOnly` enforced? | Whether the customer-code design (read-only repo + writable work dir + log dir) is buildable at all. boxlite caps at 2 |
| 3 | Do guest writes reach a host `inotify` watcher? | Whether the Alloy log-shipping design carries over unchanged |
| 4 | Is the entrypoint's stdout/stderr captured on the host? | Whether workloads still have to write files to `/var/log/kinotic`, or logs come for free |
| 5 | Exit code of a finished workload; stop/start with rootfs intact; state visible to a new process | `WorkloadStatus` gaining a real terminal state, `restartWorkload`, and `IVmProvider.recover` |
| 6 | memory / vCPU limits, guest `df`, and where the writable layer lives on the host | Whether `Workload.vcpus`/`memoryMb` map over, and how disk gets capped — a host directory can take the same XFS project quota already designed for volumes |
| 7 | Default egress, a no-egress mode, published ports, the workload's host-side address | Whether `NetworkPolicy` is implementable, and whether egress can be restricted to the api-gateway |
| 8 | Cold boot latency | Whether the developer edit/redeploy cycle stays fast |

## What this harness does not answer

- **How the provider drives it.** The probes shell out to `nerdctl` because that is the
  quickest way to ask a capability question. A real `KataProvider` would talk to containerd's
  gRPC API instead, and the ergonomics of doing that from Bun is its own question — boxlite's
  Node SDK is a genuine advantage this stack has to earn back.
- **Egress allowlisting.** Kata has no equivalent of boxlite's `allowNet`; restricting a
  workload to the api-gateway is host firewall work against the address phase 7 reports. The
  probe establishes that the address exists and is stable, not that the rules are written.
- **Multi-tenant hardening.** Whether nodes stay single-tenant is a scheduling decision above
  the provider, and applies whichever runtime wins.
