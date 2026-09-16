A node keeps every project's checkout directly under its workload data directory, keyed by
project alone:

```java
// kinotic-system-api/.../ProjectDeployJobDefinitionFactory.java:216
new DeployTarget(node.getId(),
                 node.getWorkloadDataDir() + "/projects/" + projectId,
                 syncWorkloadId,
                 uiPublishWorkloadId)
```

so `/var/lib/kinotic/workloads/projects/<project>` holds projects of every organization side by
side, and nothing on the filesystem says which organization owns which. I want two things:
every workload directory of an organization under one directory for that organization, and a
bun package cache shared by that organization's sync workloads and no one else's.

What I already know, so you don't re-derive it:

- The node accepts any mount whose host path is inside its workload data directory and creates
  writable ones on demand (`VolumeMountManager.prepare`), and it caps a mount with an XFS
  project quota on that directory (`MountQuotaManager`). Two workloads may mount the same host
  directory; what a shared directory needs is one cap, set once, not one per workload.
- The sync runner spawns `bun install` with its own environment spread into the child
  (`kinotic-js/workload-runner/src/sync.ts:47` and `:198`), so `BUN_INSTALL_CACHE_DIR` in the
  workload's environment is all bun needs to use another directory.
- The sync VM's root filesystem is where bun keeps its cache today, which is why
  `DeploymentProperties.syncDiskSizeMb` defaults to 4 GB; with the cache on a mount it can
  come back down to the runtime VM's 1 GB.
- A cache shared across organizations is a channel between tenants: an install that hits the
  cache takes the tarball as it finds it, so one organization's project could feed another's
  dependency tree. The organization is the trust boundary, so the cache is per organization
  and never wider.
- `kinotic_project_deployment.hostDir` is persisted, and `resolveTarget` reuses an existing
  deployment's directory. Nothing is deployed yet, and the version is a SNAPSHOT, so the
  layout can change without a migration of existing directories; the design page's cutover
  step already scrubs `hostDir` on every row.

Do this:

1. Lay the node out by organization: `<workloadDataDir>/<organizationId>/projects/<projectId>`
   for a project's checkout, and `<workloadDataDir>/<organizationId>/bun-cache` for the
   organization's package cache. `DeployTarget` gets the organization's directory as its own
   field rather than callers deriving it from `hostDir` with `..`; the project directory is
   formed from it in one place.
2. The sync workload mounts the organization's cache writable at a fixed guest path and sets
   `BUN_INSTALL_CACHE_DIR` to it; the runtime workload does not mount it. The cap on the cache
   is a new `DeploymentProperties` field beside `syncMountLimitMb`, applied by the node as the
   directory's project quota; find out what `MountQuotaManager` does when two live workloads
   declare the same directory with the same cap, and make it idempotent if it is not.
3. Bring `syncDiskSizeMb` down to 1 GB once the cache is off the root, and say in its Javadoc
   what remains on the root.
4. The node's own bookkeeping, if any of it names the project directory (the reaper, the
   quota state, the publish workload's mount), follows the new path. `verify-node.sh` and the
   vm-node README describe the layout an operator sees on disk, so an audit reads
   `ls /var/lib/kinotic/workloads/<org>` and finds everything that organization has on the node.
5. Update the configuration page's table, the project-publishing design page where it names
   `hostDir`, and the development-server page's cutover step, which scrubs `hostDir`.

Keep the fix minimal: the organization directory and the cache are the change; no cache
warming, no cross-node sharing, no eviction beyond what the quota enforces.
