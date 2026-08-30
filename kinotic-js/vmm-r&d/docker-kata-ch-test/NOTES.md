# Notes on the kata release, and why this kit is amd64 only

Two findings from porting this kit forward. One changed what it installs; the other is why it
refuses to run anywhere but x86_64.

## Kata 4.1.0 removed the Go runtime, and the kit now pins to it

`setup-node.sh` used to resolve the release from GitHub at run time, so the same script
installed a different bundle depending on when it ran. It now pins `KATA_VERSION`. A node's
runtime is a decision rather than whatever upstream released most recently, `latest` is a
supply-chain surface a pin closes, and 4.1.0 is the first release carrying the fix for
`CVE-2026-77176` (GHSA-fmg6-v47x-52wr, high), which affects every version up to 4.0.0.

Kata 4.1.0 (21 Aug 2026) removed the Go runtime from `kata-static` on **every** architecture:

| | 3.32.0 | 4.0.0 | 4.1.0 |
|---|---|---|---|
| `/opt/kata/bin/containerd-shim-kata-v2` (Go) | yes | yes | **gone** |
| `/opt/kata/bin/kata-runtime` | yes | yes | **gone** |
| `/opt/kata/runtime-rs/bin/containerd-shim-kata-v2` | yes | yes | yes |
| `configuration-clh.toml` (Go path) | yes | yes | **gone** |
| `runtime-rs/configuration-clh-runtime-rs.toml`, amd64 | yes | yes | yes |

The amd64 bundle went from 263 entries in 4.0.0 to 171; `/opt/kata/bin` now holds only
hypervisor binaries. So the Azure setup did work, and still works on the release it was
provisioned with — what changed is upstream. On 4.1.0 the unmodified script stopped at

```bash
[ -x /opt/kata/bin/containerd-shim-kata-v2 ] || fail "the bundle provided no kata shim"
```

a loud failure that left the node unregistered. The kit now targets runtime-rs directly, which
keeps its own config tree: the shim reads `/etc/kata-containers/runtime-rs/configuration.toml`,
and its defaults are the `configuration-*-runtime-rs.toml` files rather than the ones beside
them.

### Each run installs exactly one bundle

`tar -C /` unpacks over `/opt/kata` without deleting what a previous release left there, and
the releases do not ship the same files. A node provisioned before 4.1.0 keeps that release's
Go shim *and* its `configuration-clh.toml`, both of which the runtime selection would find and
prefer — so re-running the script to move a node onto 4.1.0 would leave it running the 4.0.0
build against a 4.1.0 kernel and image.

That is not only a staleness problem. Re-provisioning is exactly how an operator picks up the
CVE fix, and it was the one operation that would have quietly kept the vulnerable runtime.
`setup-node.sh` removes `/opt/kata` before extracting.

### A pin needs someone watching the advisories

Tracking `latest` is what let 4.1.0 remove the Go runtime without warning, which is why the
version is pinned — but it is also what would have moved a node off an advisory-affected
release with nobody doing anything. Pinned, that no longer happens on its own: bumping
`KATA_VERSION` is the step that picks up the next fix, and something has to prompt it.

## Why aarch64 cannot host a Cloud Hypervisor node

The kit refuses to provision anything but x86_64. Three independent reasons, any one of which
is enough.

**Azure arm64 has no nested virtualization.** A CLOUD_HYPERVISOR node needs `/dev/kvm`, and
Microsoft's size documentation is explicit — `Dpsv5-series` and `Dpsv6-series` (Ampere) both
say `Nested Virtualization: Not supported`, where `Dv5-series` says `Supported`. There is
nowhere on Azure for an arm64 node to run.

**Upstream does not build it.** runtime-rs generates a hypervisor's config only when the arch
makefile names that hypervisor's binary:

```make
# src/runtime-rs/Makefile
ifneq (,$(CLHCMD))
    CONFIG_FILE_CLH = configuration-clh-runtime-rs.toml
```

`arch/x86_64-options.mk` sets `CLHCMD := cloud-hypervisor`; `arch/aarch64-options.mk` sets
`QEMUCMD`, `DBCMD` and `FCCMD` and no `CLHCMD`. The arm64 bundle therefore ships the
`cloud-hypervisor` binary and a shim with CH support compiled in, but no config that selects
it — the combination is neither built nor tested there.

**A workload gets no network, and that one is not a packaging gap.** Kata folds a NIC into the
VM's initial configuration when the namespace already has an interface, and otherwise hot-plugs
it after boot. Docker populates a container's namespace only after the VMM exists, so the
hot-plug path is the one it takes — and on aarch64 Cloud Hypervisor direct-boots the kernel
with a device tree, so without UEFI the guest cannot locate ACPI tables and disables ACPI,
taking the GED with it:

```
[    0.000000] Machine model: linux,dummy-virt
[    0.000000] efi: UEFI not found.
[    0.582970] ACPI: Interpreter disabled.
```

The hot-plug succeeds host-side — CH accepts the device and assigns it a PCI BAR — and the
guest never sees it, so the workload comes up with only `lo`. Reproduced on both kata runtimes,
runtime-rs from 4.1.0 and the Go runtime from 4.0.0. Cold-plugged virtio-net works on the same
hypervisor and the same kernel, which makes this a kata/CH integration gap rather than a
hardware one — and it would bite a bare-metal arm64 host just as hard, having nothing to do
with nesting.

Making it work needs the workload attached to a namespace an ordinary container already owns,
the way CRI's pod sandbox does. That was built and verified, then removed with the rest of the
arm64 support: it is a real accommodation to carry for an architecture with nowhere to run.
Local development uses the `BOXLITE` provider instead, which has its own networking and a much
smaller footprint.

If arm64 is ever worth revisiting, the two things to check first are whether
`aarch64-options.mk` has gained a `CLHCMD`, and whether kata cold-plugs the NIC — the machinery
for that already exists in `get_shared_devices()` and is bypassed only because Docker populates
the namespace late.

## Azure-specific pieces: installed, inert off Azure

`kinotic-node-firewall` drops guest traffic to Azure IMDS (`169.254.169.254`) and the WireServer
(`168.63.129.16`, ports 80 and 32526). Neither address exists elsewhere. The rules install and
verify normally — they are unconditional `iptables` DROPs — so nothing is skipped or stubbed;
they simply protect nothing off Azure. Left in place so every node is provisioned by the same
path.

## One change to the requirement checks

R1's third assertion read `DMI:` out of the guest's `dmesg` to prove the hypervisor was Cloud
Hypervisor. That proved the guest's own belief about itself, but not that the VMM backed the
workload under test. It now ties the two together from the host side: the shim names the CH API
socket after the container id —

```
/opt/kata/bin/cloud-hypervisor --api-socket /run/kata/<container-id>/ch-api.sock
```

— so the test walks `/proc/*/exe` for a `cloud-hypervisor` and requires one whose command line
names this container. The previous check proved *a* CH process existed somewhere on the node.
