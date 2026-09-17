# docker-kata-ch-test

Requirement verification for a `CLOUD_HYPERVISOR` node: real micro VMs run against a node
provisioned by `deployment/vm-node`, and each workload requirement is checked against
something observed — bytes that landed on disk, a process's executable, a line in a log
file — never against a flag we set. Results are written to `last-run.txt`.

```bash
sudo ./deployment/vm-node/setup-node.sh     # from the repository root, on the node
sudo ./deployment/vm-node/verify-node.sh
sudo bun run src/requirements-test.ts       # here
```

Unlike [`../kata-fc-test`](../kata-fc-test/README.md) and [`../boxlite-test`](../boxlite-test/README.md),
which were evaluation harnesses answering *can this runtime do X*, this test is the outcome of
that evaluation and runs against the node kit a production node is built with.

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

With egress default-deny on, a container started outside the vm-manager has no network, which
is the point; the test starts its own containers, and its network probe is given the node's
resolver on the bridge address, which the firewall floor admits, and nothing else. R1's
hypervisor assertion ties the guest to the host side: the shim names the Cloud Hypervisor API
socket after the container id, so the test walks `/proc/*/exe` for a `cloud-hypervisor` whose
command line names this container.
