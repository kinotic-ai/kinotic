# docker-kata-ch

Node provisioning and requirement verification for the `CLOUD_HYPERVISOR` provider: workloads
run as [Kata Containers](https://katacontainers.io/) microVMs on
[Cloud Hypervisor](https://www.cloudhypervisor.org/), driven through the Docker Engine API.

Unlike [`../kata-fc-test`](../kata-fc-test/README.md) and [`../boxlite-test`](../boxlite-test/README.md),
which were evaluation harnesses answering *can this runtime do X*, this folder is the outcome
of that evaluation: the setup a production node actually needs, and the checks that say whether
a given node still meets it.

```bash
sudo ./setup-node.sh                       # provision; idempotent, safe to re-run
sudo ./verify-node.sh                      # assert every invariant — run after any reboot
sudo bun run src/requirements-test.ts      # run real microVMs and check each requirement
```

Needs Ubuntu 22.04 with nested virtualization, root, and outbound internet. Kata's release is
pinned in the script and printed, so a run reports exactly what it installed and two nodes
provisioned months apart are running the same thing. Bumping it is an edit, made against the
release notes and the advisories for the version being left behind.

## Why Docker and not containerd's CRI

containerd applies `base_image_size` **per snapshotter**, not per container, so a CRI-driven
node can cap every workload's rootfs at one size or none — it cannot honour a per-workload
`Workload.diskSizeMb`. Docker's `--storage-opt size` can, and Docker has a maintained Node
client where CRI has none. The workload still runs through containerd and the Kata shim, so
the isolation is identical; only the control plane differs.

## What the node setup establishes

Each of these fails **silently** if it is missing — the node keeps accepting workloads and
simply stops enforcing something — which is why `verify-node.sh` asserts them rather than
assuming them.

| Setting | Without it |
|---|---|
| `/etc/kata-containers/configuration.toml` → the clh config | The shim runs QEMU while every version string still says cloud-hypervisor |
| XFS + `prjquota` data root, with an `fstab` entry | `--storage-opt size` stops working, and the mount is lost on the next reboot |
| `RequiresMountsFor` drop-in on `docker.service` | dockerd can start before its data root is mounted |
| `br_netfilter` + `bridge-nf-call-iptables=1` | Firewall rules are accepted, appear in the table, and are bypassed |
| `"icc": false` | Every workload can reach every other workload's listening ports |
| `kinotic-node-firewall.service` | Guests can read Azure IMDS, its signed attested document, and the WireServer goal state |
| `dnsmasq` on the bridge address, `/etc/dnsmasq.d/kinotic-node.conf` | A hostname in an allowlist cannot be enforced, and the vm-manager refuses any workload naming one |
| `live-restore` | A dockerd restart kills every workload on the node |

### The firewall floor

`kinotic-node-firewall` runs `After=docker.service`, because Docker rebuilds its chains when
the daemon starts and would otherwise drop these rules on every restart. `DOCKER-USER` is
consulted from `FORWARD`, so everything in it governs guest traffic only — the host's own
processes go out through `OUTPUT` and keep their access. That is what lets the vm-manager
read IMDS for its own Entra token on a node where no workload can.

Nothing in the floor carries per-workload state, so there is nothing to get out of sync with a
workload's lifecycle. `INPUT` drops everything from the bridge subnet, so a guest cannot dial the
node's own services, with one exception inserted above the drop: UDP 53 to the bridge address,
where the workload resolver listens.

### The workload resolver

`dnsmasq` listens on the docker bridge address (`172.17.0.1` unless the bridge is configured
otherwise), bound with `bind-dynamic` so it survives `docker0` coming up after it, forwarding to
whatever `/run/systemd/resolve/resolv.conf` lists — the VNet resolver on Azure. Every workload is
given that address as its only resolver (`KINOTIC_WORKLOAD_DNS`), so an address a guest connects
to by name is one this dnsmasq answered.

That is what makes a hostname in `network.allowedHosts` enforceable. The vm-manager keeps one
ipset per allowed name and writes `/etc/dnsmasq.d/kinotic-egress.conf`, an `ipset=` directive
per name telling dnsmasq which sets its answers go into; a per-workload `iptables -m set` rule
then matches the set. dnsmasq only reads directives at startup, so the vm-manager restarts it
when a workload is allowed a name the file lacks — and only then: a name stays configured after
its last workload is released, until the next `reconcile` on vm-manager start, which is what keeps
a deployment's sync workload from restarting the resolver on every run. `cache-size=0` is
load-bearing: dnsmasq writes into a set only while processing an upstream reply, never when
answering from its cache.

Set entries carry a 300s timeout that every answer refreshes, and each workload allowed a name
also gets a conntrack `ESTABLISHED,RELATED` accept, so a connection opened while the entry held
is not cut when it expires.

**Egress default-deny** is written but off by default. Once the vm-manager writes per-workload
egress rules, enable it:

```bash
sudo mkdir -p /etc/kinotic && sudo touch /etc/kinotic/egress-default-deny
sudo systemctl restart kinotic-node-firewall
```

It is *appended*, so it sits below any per-workload `ACCEPT` the vm-manager inserts with `-I`.
Per-workload rules are placed immediately above it and below the node's own metadata drops, so
a policy of `0.0.0.0/0` means the whole internet except the host's identity. A policy that
names a protected address exactly is placed at the top instead, where it overrides the drop —
which only the server can ask for, and which the node logs.
A workload whose rules were never applied — provider died mid-start, address recycled, a
container started outside the vm-manager — then gets no network at all rather than
unrestricted egress.

With it on, a container started **outside** the vm-manager has no network, which is the point.
The requirements test starts its own containers; its network probe is given the node's resolver,
which the floor admits, and nothing else.

## What the requirements test proves

| Requirement | Checked by |
|---|---|
| R1 Customer code isolated in a microVM | Guest kernel differs from the host's, a `cloud-hypervisor` process backs it, and that process's command line names this container |
| R2 Logs shipped host-side | stdout and stderr land in the container's json-file with `stream` labels, and `LogPolicy` maps to Docker's rotation options |
| R3 Reaches as little as possible | IMDS and the WireServer refuse the guest, the node's resolver still answers, one workload cannot reach another's port, and `--network none` denies everything |
| R4 Fast edit/redeploy | A host-side edit under the shared mount is visible to the next workload with no image rebuild |
| R5 Read-only app code + writable logs | Both mounts present in one workload, and `readOnly` refused from inside the guest |
| R6 `VolumeMount.sizeLimitMb` | A 200MB write into a 64MB project quota lands 67043328 bytes |
| R7 Server sets the filesystem size | A 1500MB write into a 1024MB rootfs lands 1073676288 bytes |

Plus the lifecycle the provider depends on: a terminal exit code, a restart that keeps the
writable layer, and workloads a fresh process can discover.

Assertions are against something observed — bytes that landed on disk, a process's executable,
a line in a log file — never against a flag we set. Results are written to `last-run.txt`.

## Known limits

- **A hostname in an allowlist covers its subdomains, and only the node's resolver's
  answers.** dnsmasq matches `github.com` for `api.github.com` too, and only what it answered
  is permitted: a guest that resolves elsewhere, or connects to an address a CDN handed someone
  else, is denied. Same-VNet targets are still best expressed as CIDRs, which need no lookup.
- **Allowing a new name restarts dnsmasq.** Every guest on the node is without a resolver for
  the restart, and a lookup landing in that gap fails. Names already configured — every run of
  the same deployment — cost nothing.
- **User-defined Docker networks break DNS under Kata.** Docker injects `127.0.0.11`, whose
  resolver lives in the host netns and is unreachable from inside the VM; `--dns` sets only the
  upstream it forwards to. Workloads stay on the default bridge.
- **A workload that fills its rootfs to the cap cannot restart** — Docker cannot create the
  overlay `merged` directory inside an exhausted quota. Correct enforcement, but worth knowing.
- **`icc: false` beats an allowlist entry for an on-node peer.** Docker's inter-container
  block sits below `DOCKER-USER`, so an `ACCEPT` naming another container's address on this
  node does not take effect. Destinations off the bridge — the api-gateway behind its load
  balancer, a VNet CIDR, the internet — are unaffected. Do not colocate the api-gateway with
  workloads.
- **Host networking would bypass the floor.** A container sharing the host netns sends through
  `OUTPUT`, not `FORWARD`, so `DOCKER-USER` never sees it. The provider emits only `bridge` or
  `none`, so this cannot happen today.
