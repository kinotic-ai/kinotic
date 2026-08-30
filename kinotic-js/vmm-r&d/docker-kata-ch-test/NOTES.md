# Porting notes — running this kit on aarch64

The kit was written against x86_64 Ubuntu 22.04 on Azure. This records what had to change to
run it on aarch64 Ubuntu 24.04, one upstream packaging change that affects the x86_64/Azure
path as much as the arm64 one, and why workload networking needs a different arrangement here
than on x86_64.

Reference environment: Ubuntu 24.04 arm64 guest (kernel 6.8.0-138, 10 vCPU / 31 GB / 154 GB)
under UTM 4.7.5 on the Apple Virtualization backend, on an M4 Max running macOS 26.5. UTM sets
`VZGenericPlatformConfiguration.isNestedVirtualizationEnabled` whenever the host supports it, so
`/dev/kvm` is present in the guest with no extra configuration. Installed there:
kata-containers 4.1.0, cloud-hypervisor v51.1, Docker 29.7.2.

## Kata 4.1.0 removed the Go runtime — this breaks the x86_64 path too

`setup-node.sh` resolves the Kata release from GitHub at run time and pins nothing, so the same
script installs a different bundle depending on when it runs. Kata 4.1.0 (21 Aug 2026) removed
the Go runtime from `kata-static` on **every** architecture. Listing the release assets:

| | 3.32.0 | 4.0.0 | 4.1.0 |
|---|---|---|---|
| `/opt/kata/bin/containerd-shim-kata-v2` (Go) | yes | yes | **gone** |
| `/opt/kata/bin/kata-runtime` | yes | yes | **gone** |
| `/opt/kata/runtime-rs/bin/containerd-shim-kata-v2` | yes | yes | yes |
| `configuration-clh.toml` (Go path) | yes | yes | **gone** |
| `runtime-rs/configuration-clh-runtime-rs.toml`, amd64 | yes | yes | yes |
| `runtime-rs/configuration-clh-runtime-rs.toml`, arm64 | n/a | n/a | **never generated** |

The amd64 bundle went from 263 entries in 4.0.0 to 171 in 4.1.0; `/opt/kata/bin` now holds only
hypervisor binaries (`qemu-system-x86_64*`, `openvmm`, `cloud-hypervisor`).

So the Azure setup did work, and still works on the release it was provisioned with. What
changed is upstream: the next time an Azure node is provisioned, `latest` is 4.1.0 and the
unmodified script stops at

```bash
[ -x /opt/kata/bin/containerd-shim-kata-v2 ] || fail "the bundle provided no kata shim"
```

That is a loud failure, not a silent misconfiguration — setup aborts and the node is not
registered. The changes below restore that path: on 4.1.0 amd64 the runtime-rs branch is taken
and upstream *does* ship `configuration-clh-runtime-rs.toml` for x86_64, so nothing is rendered
and nothing is guessed. On a release that still carries the Go runtime the Go branch is taken
first, exactly as before.

### Two follow-ups for the maintainers

- **Consider pinning `KATA_VERSION`.** A node's runtime silently changing underneath the same
  script is what produced this. The README calls out resolving the release at run time as
  deliberate — worth revisiting now that a minor release can remove a runtime.
- **Extraction does not remove the previous bundle.** `tar -C /` unpacks over `/opt/kata`
  without deleting what a previous release left there, so a node provisioned on 4.0.0 and
  re-run on 4.1.0 keeps a stale Go shim *and* a stale `configuration-clh.toml`, and the Go
  branch matches both — pairing a 4.0.0 shim with a 4.1.0 guest kernel and image. Adding
  `rm -rf /opt/kata` before the untar would make each run install exactly one bundle. Left out
  here because it changes behaviour on production nodes and is the maintainers' call.

## Workload networking on arm64: cause and fix

`setup-node.sh` and `verify-node.sh` pass and kata-clh micro VMs boot, but a workload started
the way a production node starts one comes up with only `lo`:

```
$ docker exec <workload> ls /sys/class/net
lo
```

### Why

Kata attaches a NIC one of two ways, and Docker forces the one that does not work here.
`src/runtime-rs/crates/hypervisor/src/ch/inner_device.rs`:

```rust
pub(crate) async fn add_device(&mut self, device: DeviceType) -> Result<DeviceType> {
    if self.state != VmmState::VmRunning {
        match device {
            // queued, and folded into the initial VmConfig by get_shared_devices() — cold-plug
            DeviceType::Network(_) => self.pending_devices.insert(0, device.clone()),
            ...
        }
        return Ok(device);
    }
    self.handle_add_device(device).await          // VM already up — hotplug over the CH API
}
```

Docker populates a container's namespace only after the VMM exists, which kata's own rescan
helper states in `virt_container/src/sandbox.rs`:

> Build a network rescan config targeting the hypervisor's network namespace. Docker 26+
> bind-mounts `/proc/<vmm_pid>/ns/net` and hypervisor netns is where the interfaces will
> appear — regardless of whether we earlier created a placeholder netns (network_created)

So the VM boots with no NIC, the post-start rescan finds `eth0`, and kata hot-plugs it. The
hot-plug succeeds host-side — Cloud Hypervisor accepts the device and assigns it a PCI BAR:

```
$ curl --unix-socket /run/kata/$ID/ch-api.sock http://localhost/api/v1/vm.info
config.net:  [{"mac": "be:31:e1:1f:93:8f", "id": "_net3", ...}]
device_tree: _virtio-pci-_net3 -> [{"PciBar": {"index": 0, "base": 1096589770752, ...}}]
```

The guest never sees it, because on aarch64 Cloud Hypervisor direct-boots the kernel and
describes the machine with a device tree. Without UEFI the kernel cannot locate ACPI tables and
disables ACPI, taking the ACPI GED with it — and GED is the only thing that would announce a
PCI device appearing:

```
[    0.000000] Machine model: linux,dummy-virt
[    0.000000] efi: UEFI not found.
[    0.582970] ACPI: Interpreter disabled.
[    0.158116] pci-host-generic 30000000.pci: host bridge /pci@30000000 ranges:
```

The guest kernel is built for hot-plug (`CONFIG_HOTPLUG_PCI=y`, `CONFIG_HOTPLUG_PCI_ACPI=y`);
the driver has no ACPI to bind to. `cloud-hypervisor --platform` offers `num_pci_segments`,
`iommu_segments` and DMI strings — no ACPI switch. Reproduced on **both** kata runtimes,
runtime-rs from 4.1.0 and the Go runtime from 4.0.0, so it is not a runtime-rs regression.

Only the hot-plug path is affected. Cold-plugged virtio-net works on the same hypervisor and
the same guest kernel:

```
$ /opt/kata/bin/cloud-hypervisor --kernel /opt/kata/share/kata-containers/vmlinux.container \
    --cmdline "console=ttyAMA0 earlycon=pl011,mmio,0x9000000 panic=1" \
    --cpus boot=1 --memory size=512M --net tap=chtest0,mac=12:34:56:78:9a:bc --serial tty --console off
[    0.000000] Machine model: linux,dummy-virt      # same FDT-only boot, no ACPI
[    0.867925] pci 0000:00:01.0: [1af4:1041] type 00 class 0x020000 conventional PCI endpoint
[    1.015304] virtio-pci 0000:00:01.0: enabling device (0000 -> 0002)
```

### The fix

Give the workload a namespace that is already populated, and kata takes the cold-plug branch:

```bash
docker run -d --name anchor -p 18081:8080 --runtime runc alpine sleep 2147483647
docker run -d --name wl --runtime kata-clh --network container:anchor alpine ...
```

```
0x0d57 0x1042 0x1041 0x1044 0x105a 0x1053      # 0x1041 = virtio-net, present at boot
eth0: inet 172.17.0.2/16 ... state UP
default via 172.17.0.1 dev eth0
```

This is the arrangement CRI produces with its pod sandbox, which is why Kubernetes nodes never
hit this. `docker create` + `docker start` and user-defined Docker networks were both tried and
still hot-plug.

The vm-manager implements it as `NetnsAnchorManager`, used only when
`KINOTIC_NODE_MODE=DEVELOPMENT`; a production node hot-plugs exactly as before. See
`website/content/02.platform/03.configuration.md`.

**Restarting a workload needs the namespace cleared first.** Kata does not detach its endpoint
from a namespace it did not create, leaving the tap device and the tc ingress qdisc behind; the
next start fails on `unsupported link type: tuntap`, or `add virt ingress: File exists`.
Removing both is enough — the workload then restarts with its address and its writable layer:

```bash
nsenter -t <anchor-pid> -n ip link del tap0_kata
nsenter -t <anchor-pid> -n tc qdisc del dev eth0 ingress
```

`NetnsAnchorManager.clean()` does this before every start, and
`test/CloudHypervisorProvider.networking.test.ts` covers both the address and the restart on a
real node.

### A colocated server needs the node's own address permitted

The floor drops everything from the workload bridge to the node itself:

```
-A INPUT -s 172.17.0.0/16 -j DROP        # "host services shielded"
```

Per-workload egress rules cannot lift it. They go in `DOCKER-USER`, which is consulted from
`FORWARD`, and traffic addressed to the node never reaches `FORWARD` — so a workload cannot
reach a server on its own node even when the server put that address in `allowedHosts`.

That is correct for Azure, where the server is not on the node; the README says not to colocate
the api-gateway with workloads. Colocation is a development arrangement, so it is gated by the
same switch as everything else development-only — `KINOTIC_NODE_MODE=DEVELOPMENT`, with no
marker file or second knob. The vm-manager then writes an `INPUT` rule for the workload that
named the address, released with that workload:

```
PRODUCTION  : rule written in DOCKER-USER, none in INPUT, node unreachable
DEVELOPMENT : reachable by the workload that named it, not by one that did not
release     : INPUT rule gone, unreachable again
```

`test/EgressPolicyManager.colocated.test.ts` covers all three against a real server on the node.
Both cases give the workload a namespace anchor, so they differ only in the environment rather
than in whether the guest has a NIC at all.

### What this does to the requirement results

`src/requirements-test.ts` starts workloads the way a production node does, with no anchor, so
on arm64 it reports `REQUIREMENTS NOT MET — 18 passed, 1 failed`, the failure being R3 "DNS
still resolves". That result is accurate: the arrangement it tests genuinely has no networking
on this architecture. Four other R3 assertions pass **vacuously** and must not be read as
evidence on this node — a workload with no NIC reaches nothing, so "Azure IMDS unreachable",
"WireServer unreachable", "one workload cannot reach another" and "NetworkMode.DISABLED denies
everything" would pass even if the policy behind them were absent.

The test was deliberately left alone: making it use an anchor would have it assert something
no production node does.

## Changes made

### `setup-node.sh`

1. **Kata asset architecture.** The release asset was selected with a hardcoded
   `contains("-amd64.tar")`. Now `uname -m` is mapped to the Go arch name Kata uses in the asset
   name (`x86_64`→`amd64`, `aarch64`→`arm64`), and an unknown machine fails rather than silently
   matching nothing.

2. **Two runtimes, two config trees.** The script assumed the Go runtime: shim at
   `/opt/kata/bin/containerd-shim-kata-v2`, config override at
   `/etc/kata-containers/configuration.toml`. runtime-rs uses
   `/opt/kata/runtime-rs/bin/containerd-shim-kata-v2` and
   `/etc/kata-containers/runtime-rs/configuration.toml` (confirmed against the search order
   compiled into the shim). Which shim is present now selects the shim path, the config
   directory and the clh config filename together. The Go runtime is still tested first, so a
   release that ships it is provisioned exactly as before.

3. **Rendering the clh config when the bundle omits it.** runtime-rs generates a hypervisor's
   config only when the arch makefile names that hypervisor's binary:

   ```make
   # src/runtime-rs/Makefile
   ifneq (,$(CLHCMD))
       CONFIG_FILE_CLH = configuration-clh-runtime-rs.toml
   ```

   `arch/x86_64-options.mk` sets `CLHCMD := cloud-hypervisor`; `arch/aarch64-options.mk` sets
   `QEMUCMD`, `DBCMD` and `FCCMD` but no `CLHCMD`. So the arm64 bundle ships the
   `cloud-hypervisor` binary and a shim with CH support compiled in, but no config that would
   select it — the only runtime-rs configs present are dragonball, qemu, openvmm and rs-fc. The
   script now renders `configuration-clh-runtime-rs.toml` from upstream's own template at the
   exact release tag it just installed, substituting the values from upstream's
   `src/runtime-rs/Makefile` and `arch/aarch64-options.mk`. Any placeholder left unsubstituted
   deletes the file and fails the run, so a template that gains a variable cannot produce a
   half-rendered config. **This never fires on amd64**, where upstream ships the config.
   **Delete it once upstream sets `CLHCMD` for aarch64.**

4. **Version reporting without `kata-runtime`.** The step ended with
   `/opt/kata/bin/kata-runtime env | grep 'Version = "cloud-hypervisor'`. `kata-runtime` is a
   Go-runtime binary and is absent from 4.1.0, so the proof that the resolved config really is
   the clh one now comes from the config itself — the linked `configuration.toml` must contain a
   `[hypervisor.clh]` section — plus the shim path and `cloud-hypervisor --version`.

5. **`chmod 0755` on the VMM binaries** moved above the shim detection; it was previously
   unreachable when the shim assertion failed first.

### `verify-node.sh`

6. **Config path.** Same two-runtime split as above: the check reads
   `/etc/kata-containers/runtime-rs/configuration.toml` when it exists and
   `/etc/kata-containers/configuration.toml` otherwise.

7. **New assertion, "cloud-hypervisor selected".** The old check only proved the symlink *name*
   contained `clh`. It now also greps the resolved file for `[hypervisor.clh]`, which is what
   the shim actually keys on.

### `src/requirements-test.ts`

8. **R1 "the guest names its own hypervisor" was x86-only.** It read `DMI:` out of the guest's
   `dmesg`; an aarch64 Cloud Hypervisor guest boots from a device tree and has no SMBIOS, so the
   line never exists. Replaced with a check that holds on both architectures: the shim names the
   CH API socket after the container id, so the workload can be tied to its own VMM host-side —

   ```
   /opt/kata/bin/cloud-hypervisor --api-socket /run/kata/<container-id>/ch-api.sock
   ```

   The test walks `/proc/*/exe` for a `cloud-hypervisor` and requires one whose command line
   names this container. The previous check proved *a* CH process existed somewhere on the node;
   this one proves it backs the workload under test. Note this drops a guest-side assertion in
   favour of a host-side one — if the x86 DMI evidence is wanted as well, it has to come back as
   a check that skips where there is no SMBIOS.

### `README.md`

9. R1's row in "What the requirements test proves" updated to describe the check above.

## Azure-specific pieces: installed, inert here

`kinotic-node-firewall` drops guest traffic to Azure IMDS (`169.254.169.254`) and the WireServer
(`168.63.129.16`, ports 80 and 32526). Neither address exists off Azure. The rules install and
verify normally — they are unconditional `iptables` DROPs — so nothing was skipped or stubbed;
they simply protect nothing on this node. Left in place so the local node and a production node
are provisioned by the same path.

Egress default-deny was left off, as shipped.

## A `set -e` trap in the new render block

Worth knowing if that block is edited: the "no placeholders left" check is

```bash
LEFTOVER="$(grep -oE '@[A-Z0-9_]+@' "$CLH_CONF" | sort -u | tr '\n' ' ' || true)"
```

The `|| true` is load-bearing. `grep` exits 1 when it matches nothing, which here is the success
case, and under `set -e` an assignment takes the exit status of its command substitution — so
without it the script aborts precisely when the render worked.
