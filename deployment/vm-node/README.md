# vm-node

Provisioning for a node that runs Kinotic workloads with the `CLOUD_HYPERVISOR` provider:
each workload is a [Kata Containers](https://katacontainers.io/) micro VM on
[Cloud Hypervisor](https://www.cloudhypervisor.org/), driven through the Docker Engine API by
the vm-manager. The same kit provisions the development server's nodes (bare-metal machines
beside the Proxmox host, configured from `deployment/terraform/proxmox`'s `vm_manager_env`
output) and any other node, cloud or bare metal.

```bash
sudo ./setup-node.sh            # provision; idempotent, safe to re-run
sudo touch /etc/kinotic/egress-default-deny && sudo systemctl restart kinotic-node-firewall
sudo ./verify-node.sh           # assert every invariant — run after any reboot
sudo ./install-vm-manager.sh    # the vm-manager under /opt/kinotic/vm-manager, as a systemd service
```

Needs Ubuntu 26.04 LTS on x86_64, the minimized image included, with `/dev/kvm` (nested
virtualization on a VM), root, and outbound internet; 22.04 and 24.04 run the same kit. Kata's release is pinned in `setup-node.sh` and printed, so a run reports
exactly what it installed and two nodes provisioned months apart are running the same thing.
Bumping it is an edit, made against the release notes and the advisories for the version
being left behind.

## Disks

`setup-node.sh` needs Docker's data root on XFS mounted with `prjquota`; that is what caps a
workload's rootfs (`Workload.diskSizeMb`) and its writable mounts (`VolumeMount.sizeLimitMb`).
Two ways to get there:

- **A disk of the node's own.** Mount it at `/var/lib/docker` with `prjquota` before running
  the script, with an fstab entry that carries `prjquota`; the script keeps the mount and only
  verifies it. Give the workload data directory (`KINOTIC_WORKLOAD_DATA_DIR`, by convention
  `/var/lib/kinotic/workloads`) the same treatment, on its own partition or the same
  filesystem, since the vm-manager refuses writable mounts it cannot cap. This is how the
  development server's nodes are installed.
- **Nothing to spare.** The script creates a 40 GB XFS loop image (`DOCKER_FS_SIZE` to change
  it) and mounts that. Fine for a cloud VM with one disk.

## Why Docker and not containerd's CRI

containerd applies `base_image_size` **per snapshotter**, not per container, so a CRI-driven
node can cap every workload's rootfs at one size or none — it cannot honour a per-workload
`Workload.diskSizeMb`. Docker's `--storage-opt size` can, and Docker has a maintained Node
client where CRI has none. The workload still runs through containerd and the Kata shim, so
the isolation is identical; only the control plane differs.

## What the node setup establishes

Each of these fails **silently** if it is missing — the node keeps accepting workloads and
simply stops enforcing something — which is why `verify-node.sh` asserts them rather than
assuming them, and why the vm-manager re-checks them on every heartbeat.

| Setting | Without it |
|---|---|
| `/etc/kata-containers/runtime-rs/configuration.toml` → the clh config | The shim runs QEMU while every version string still says cloud-hypervisor |
| XFS + `prjquota` data root, with an `fstab` entry | `--storage-opt size` stops working, and the mount is lost on the next reboot |
| `RequiresMountsFor` drop-in on `docker.service` | dockerd can start before its data root is mounted |
| `br_netfilter` + `bridge-nf-call-iptables=1` | Firewall rules are accepted, appear in the table, and are bypassed |
| `"icc": false` | Every workload can reach every other workload's listening ports |
| `kinotic-node-firewall.service` | Guests can read Azure IMDS, its signed attested document, and the WireServer goal state |
| `live-restore` | A dockerd restart kills every workload on the node |

### The firewall floor

`kinotic-node-firewall` runs `After=docker.service`, because Docker rebuilds its chains when
the daemon starts and would otherwise drop these rules on every restart. `DOCKER-USER` is
consulted from `FORWARD`, so everything in it governs guest traffic only — the host's own
processes go out through `OUTPUT` and keep their access. That is what lets the vm-manager
read IMDS for its own Entra token on a node where no workload can.

Nothing in the floor carries per-workload state, so there is nothing to get out of sync with a
workload's lifecycle.

**Egress default-deny** is written but off by default, because with nothing above it it denies
every workload. The vm-manager writes per-workload egress rules, so enable it on every node
the vm-manager runs on:

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

The Azure IMDS and WireServer drops are unconditional and install everywhere; off Azure the
addresses exist nowhere, so they protect nothing and cost nothing. Every node is provisioned by
one path.

## The vm-manager service

`install-vm-manager.sh` installs Bun, adds `@kinotic-ai/vm-manager` (its peers come with it)
under `/opt/kinotic/vm-manager`, and registers `kinotic-vm-manager.service`. The service reads:

| File | Holds |
|---|---|
| `/etc/kinotic/vm-manager.env` | Everything but credentials: provider, node id, where the server is, the workload data directory, the resolver workloads are given, the Loki/Tempo/Mimir endpoints. The installer writes a template to fill in; the development server's cloud-init writes it complete |
| `/etc/kinotic/vm-manager.secrets.env` | `KINOTIC_CLIENT_ID` and `KINOTIC_CLIENT_SECRET` of the SYSTEM machine the node connects as, created in the system console. The service does not start until this file exists |

Every variable is documented under [VM provider](https://kinotic.ai/platform/configuration#vm-provider)
and [Workload egress](https://kinotic.ai/platform/configuration#workload-egress). Set
`VM_MANAGER_VERSION` to install a specific release; re-running the installer upgrades. The
default is npm's `latest` tag, which is the last released line; a server on a pre-release
line takes the matching pre-release, `VM_MANAGER_VERSION=5.0.0-beta.19` for a 5.0.0 server.

## Kata 4.1.0 and why this kit is amd64 only

`setup-node.sh` pins `KATA_VERSION` rather than resolving the latest release: a node's runtime
is a decision, `latest` is a supply-chain surface a pin closes, and 4.1.0 is the first release
carrying the fix for `CVE-2026-77176` (GHSA-fmg6-v47x-52wr, high), which affects every version
up to 4.0.0. A pin needs someone watching the advisories: bumping it is the step that picks up
the next fix.

Kata 4.1.0 removed the Go runtime from `kata-static` on every architecture, leaving only
runtime-rs, which keeps its own config tree (`/etc/kata-containers/runtime-rs/configuration.toml`
and the `configuration-*-runtime-rs.toml` defaults). The kit targets runtime-rs directly, and
removes `/opt/kata` before extracting a bundle: `tar -C /` does not delete what a previous
release left, the releases do not ship the same files, and a node re-provisioned to pick up a
CVE fix would otherwise keep the vulnerable shim the runtime selection prefers.

The kit refuses to provision anything but x86_64, for three independent reasons:

- Azure arm64 sizes (`Dpsv5`, `Dpsv6`) have no nested virtualization, so there is nowhere on
  Azure for an arm64 node to run.
- Upstream builds no Cloud Hypervisor configuration for aarch64: `arch/aarch64-options.mk`
  sets no `CLHCMD`, so the bundle ships the binary and a shim with CH support but nothing
  that selects it.
- A workload there gets no network. Kata hot-plugs the NIC after boot because Docker
  populates the namespace late, and on aarch64 Cloud Hypervisor direct-boots without UEFI,
  so the guest disables ACPI and never sees the hot-plugged device. Reproduced on both kata
  runtimes; cold-plug works, which makes it a kata/CH integration gap. Local development uses
  the `BOXLITE` provider instead.

## Known limits

- **Egress allowlists are addresses, not names.** iptables matches addresses, and a Kata guest
  has a real network namespace with no userspace hook to match hostnames on. Same-network
  targets should be expressed as CIDRs; anything like `api.stripe.com` needs dnsmasq writing
  resolved addresses into a per-workload ipset.
- **User-defined Docker networks break DNS under Kata.** Docker injects `127.0.0.11`, whose
  resolver lives in the host netns and is unreachable from inside the VM; `--dns` sets only the
  upstream it forwards to. Workloads stay on the default bridge.
- **A workload that fills its rootfs to the cap cannot restart** — Docker cannot create the
  overlay `merged` directory inside an exhausted quota. Correct enforcement, but worth knowing.
- **`icc: false` beats an allowlist entry for an on-node peer.** Docker's inter-container
  block sits below `DOCKER-USER`, so an `ACCEPT` naming another container's address on this
  node does not take effect. Destinations off the bridge — the api-gateway on another
  machine, a network CIDR, the internet — are unaffected. Do not colocate the api-gateway
  with workloads.
- **Host networking would bypass the floor.** A container sharing the host netns sends through
  `OUTPUT`, not `FORWARD`, so `DOCKER-USER` never sees it. The provider emits only `bridge` or
  `none`, so this cannot happen today.

## Proving a node

`kinotic-js/vmm-r&d/docker-kata-ch-test` runs real micro VMs against a provisioned node and
checks each workload requirement — isolation, host-side logs, egress, mounts, quotas, exit
codes. Run it on a new node before registering it.
