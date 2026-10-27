---
title: "Configuration"
description: "Configuration reference for Kinotic OS server and services."
canonical_url: "https://kinotic.ai/platform/configuration"
---
# Configuration

> Configuration reference for Kinotic OS server and services.

<alert type="info">

Configuration reference coming soon.

</alert>

## Overview

Kinotic OS is configured through a combination of application properties, environment variables, and Helm chart values. Key configuration areas include:

- **Server configuration** — Port bindings, cluster discovery, and runtime settings
- **Elasticsearch settings** — Connection URLs, index defaults, and replica configuration
- **Authentication** — OIDC provider configuration, session management, and security policies
- **Environment variables** — Runtime overrides for containerized deployments
- **Helm values** — Kubernetes-specific configuration for resource limits, replicas, ingress, and TLS

## Loki, Tempo, and Mimir

The server reads workload logs from Grafana Loki, and workload traces and metrics from Grafana Tempo and Mimir (see [Observability](https://kinotic.ai/platform/observability#workload-logs)). Each runs multi-tenant, and the server queries the tenant the caller may see.

<table>
<thead>
  <tr>
    <th>
      Property
    </th>
    
    <th>
      Environment variable
    </th>
    
    <th>
      Helm value
    </th>
    
    <th>
      Default
    </th>
  </tr>
</thead>

<tbody>
  <tr>
    <td>
      <code>
        kinotic.managementApi.lokiUrl
      </code>
    </td>
    
    <td>
      <code>
        KINOTIC_MANAGEMENTAPI_LOKIURL
      </code>
    </td>
    
    <td>
      <code>
        kinotic.managementApi.lokiUrl
      </code>
    </td>
    
    <td>
      <code>
        http://localhost:3100
      </code>
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        kinotic.managementApi.tempoUrl
      </code>
    </td>
    
    <td>
      <code>
        KINOTIC_MANAGEMENTAPI_TEMPOURL
      </code>
    </td>
    
    <td>
      <code>
        kinotic.managementApi.tempoUrl
      </code>
    </td>
    
    <td>
      <code>
        http://localhost:3200
      </code>
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        kinotic.managementApi.mimirUrl
      </code>
    </td>
    
    <td>
      <code>
        KINOTIC_MANAGEMENTAPI_MIMIRURL
      </code>
    </td>
    
    <td>
      <code>
        kinotic.managementApi.mimirUrl
      </code>
    </td>
    
    <td>
      <code>
        http://localhost:9009
      </code>
    </td>
  </tr>
</tbody>
</table>

In docker-compose the server points at `http://loki:3100`, `http://tempo:3200`, and `http://mimir:9009`; the Helm chart defaults them to `http://loki.observability.svc:3100`, `http://tempo.observability.svc:3100`, and `http://mimir.observability.svc:9009`, the services the Azure terraform deploys in the `observability` namespace (Tempo's chart serves its HTTP API on 3100).

Every server also runs the OpenTelemetry Java agent its image embeds, which exports the server's own traces and metrics to the system tenant, `kinotic-system`, read by the system console's System **Observability** page. The Helm chart attaches it on every server and sends each signal straight to its store, with no collector between; its logs reach Loki through Alloy instead, so the agent exports none:

<table>
<thead>
  <tr>
    <th>
      Helm value
    </th>
    
    <th>
      Environment variable
    </th>
    
    <th>
      Default
    </th>
  </tr>
</thead>

<tbody>
  <tr>
    <td>
      <code>
        otel.tracesEndpoint
      </code>
    </td>
    
    <td>
      <code>
        OTEL_EXPORTER_OTLP_TRACES_ENDPOINT
      </code>
    </td>
    
    <td>
      <code>
        http://tempo.observability.svc:4318/v1/traces
      </code>
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        otel.metricsEndpoint
      </code>
    </td>
    
    <td>
      <code>
        OTEL_EXPORTER_OTLP_METRICS_ENDPOINT
      </code>
    </td>
    
    <td>
      <code>
        http://mimir.observability.svc:9009/otlp/v1/metrics
      </code>
    </td>
  </tr>
</tbody>
</table>

## VM provider

Each vm-manager node runs every workload on one VM provider, chosen by the node rather than by the workloads it is given. The node reports which one it runs when it registers, and the orchestrator surfaces it on the node in the system UI.

<table>
<thead>
  <tr>
    <th>
      Environment variable
    </th>
    
    <th>
      Values
    </th>
    
    <th>
      Default
    </th>
  </tr>
</thead>

<tbody>
  <tr>
    <td>
      <code>
        KINOTIC_VM_PROVIDER
      </code>
      
       (vm-manager)
    </td>
    
    <td>
      <code>
        BOXLITE
      </code>
      
      , <code>
        CLOUD_HYPERVISOR
      </code>
    </td>
    
    <td>
      <code>
        BOXLITE
      </code>
    </td>
  </tr>
</tbody>
</table>

<table>
<thead>
  <tr>
    <th>
      Provider
    </th>
    
    <th>
      Runs on
    </th>
    
    <th>
      Isolation
    </th>
  </tr>
</thead>

<tbody>
  <tr>
    <td>
      <code>
        BOXLITE
      </code>
    </td>
    
    <td>
      macOS and Linux
    </td>
    
    <td>
      libkrun micro VM, one per workload
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        CLOUD_HYPERVISOR
      </code>
    </td>
    
    <td>
      Linux nodes provisioned with Docker and Kata Containers
    </td>
    
    <td>
      Cloud Hypervisor micro VM, one per workload
    </td>
  </tr>
</tbody>
</table>

`CLOUD_HYPERVISOR` needs the node's Docker daemon to register a `kata-clh` runtime and to keep its data root on an XFS filesystem mounted with `prjquota`. Without the quota support the daemon refuses any workload declaring a `diskSizeMb`, since that is what caps a container's rootfs. `deployment/vm-node` in the repository provisions a node that way and installs the vm-manager as a service.

`BOXLITE` keeps box records and every guest's rootfs disk under one directory. That directory is the filesystem workload disks grow into and the one the node reports its `totalDiskMb` from, so a node taking workloads of any size wants it on a data volume rather than the root filesystem under the service account's home.

<table>
<thead>
  <tr>
    <th>
      Environment variable
    </th>
    
    <th>
      Meaning
    </th>
    
    <th>
      Default
    </th>
  </tr>
</thead>

<tbody>
  <tr>
    <td>
      <code>
        BOXLITE_HOME
      </code>
      
       (vm-manager)
    </td>
    
    <td>
      Directory boxlite keeps box records and guest rootfs disks in
    </td>
    
    <td>
      <code>
        ~/.boxlite
      </code>
    </td>
  </tr>
</tbody>
</table>

## Workload volume mounts

On every node, whichever provider it runs, a workload volume mount must live under the node's workload data directory — mounts are bound with the vm-manager's authority, so this boundary is what keeps a workload spec from mounting an arbitrary host directory. The node creates missing directories for writable mounts, refuses a read-only mount of a directory that does not exist, and reports the directory to the server at registration so deployment flows compose host paths under it. On a `CLOUD_HYPERVISOR` node the directory should sit on an XFS filesystem with `prjquota`, since that is what enforces a writable mount's `sizeLimitMb`; a node whose directory cannot enforce it reports the problem and stops taking workloads.

The default sits under the home directory of the account the vm-manager runs as, so a node creates workload directories with the authority it already has. A node taking real deployments sets it to a path on a data volume — on a `CLOUD_HYPERVISOR` node, the XFS filesystem whose project quotas enforce `sizeLimitMb`, which is not where the default lands.

<table>
<thead>
  <tr>
    <th>
      Environment variable
    </th>
    
    <th>
      Meaning
    </th>
    
    <th>
      Default
    </th>
  </tr>
</thead>

<tbody>
  <tr>
    <td>
      <code>
        KINOTIC_WORKLOAD_DATA_DIR
      </code>
      
       (vm-manager)
    </td>
    
    <td>
      Base directory every workload volume mount must live under
    </td>
    
    <td>
      <code>
        ~/.kinotic/workloads
      </code>
    </td>
  </tr>
</tbody>
</table>

## Workloads across node restarts

A node's workloads outlive its vm-manager process: the next process on the node reattaches to the VMs still running.
A node that shuts down with `KINOTIC_STOP_WORKLOADS_ON_SHUTDOWN` set instead stops every workload it runs, reports
each one's end to the server, and destroys them all, as the node `kinotic local up` starts does when
`kinotic local down` stops it.

<table>
<thead>
  <tr>
    <th>
      Environment variable
    </th>
    
    <th>
      Meaning
    </th>
    
    <th>
      Default
    </th>
  </tr>
</thead>

<tbody>
  <tr>
    <td>
      <code>
        KINOTIC_STOP_WORKLOADS_ON_SHUTDOWN
      </code>
      
       (vm-manager)
    </td>
    
    <td>
      <code>
        true
      </code>
      
       stops and destroys every workload as the node shuts down
    </td>
    
    <td>
      unset
    </td>
  </tr>
</tbody>
</table>

## Workload images

A workload's image is pulled before every start when its reference floats — no tag, or `latest` — so a node runs what the registry holds now rather than the copy it pulled first. A reference pinning a tag other than `latest` or a digest names one immutable image, and is pulled only when the node does not have it yet. Both providers apply the rule, which is the one Kubernetes uses as its default `imagePullPolicy`. A `BOXLITE` node resolves a floating tag to its current digest through the registry itself, anonymously, so its images must be public; a `CLOUD_HYPERVISOR` node has the container runtime pull the tag.

## Workload egress

A `CLOUD_HYPERVISOR` node denies workload egress by default and permits each workload only what its policy allows. The node's firewall carries the denial; the vm-manager writes one exception per allowed destination when a workload starts, and removes them when it stops.

Every destination comes from the workload's own `network.allowedHosts`, including the api-gateway — the server sets it there, because only the server knows where the gateway is. The node adds the destinations the workload cannot know: the resolver it was given, and its own OTLP endpoint for a workload that elects `telemetry` (see [Workload Traces and Metrics](https://kinotic.ai/platform/observability#workload-traces-and-metrics)).

<table>
<thead>
  <tr>
    <th>
      Environment variable
    </th>
    
    <th>
      Meaning
    </th>
  </tr>
</thead>

<tbody>
  <tr>
    <td>
      <code>
        KINOTIC_WORKLOAD_DNS
      </code>
      
       (vm-manager)
    </td>
    
    <td>
      Resolver each workload is given, permitted on port 53. On a provisioned <code>
        CLOUD_HYPERVISOR
      </code>
      
       node, the docker bridge address the node's dnsmasq listens on
    </td>
  </tr>
</tbody>
</table>

`network.allowedHosts` entries are IPv4 addresses, CIDRs, or hostnames. An address or CIDR becomes a rule naming it. A hostname is enforced through the node's resolver: every workload is given the node's dnsmasq as its only resolver, and dnsmasq writes each address it answers for an allowed name into an ipset the workload's rule matches — so what the workload reaches is what the name resolved to when it looked it up, however the name's records move, and an address it did not resolve through the node stays denied. A name covers its subdomains: `github.com` also admits `api.github.com`. A destination in the same virtual network is best expressed as the subnet's CIDR, which needs no lookup.

A name the node itself pins in `/etc/hosts` is answered from there for its workloads too, and its pinned address is admitted for that name without expiry; that is how workloads reach the api-gateway by its certificate's name on a LAN, without the traffic leaving through the router.

A node that cannot enforce a hostname — no ipset, or no dnsmasq listening on the resolver its workloads are given — refuses a workload declaring one rather than starting it with the name dropped from its policy.

<table>
<thead>
  <tr>
    <th>
      <code>
        network
      </code>
    </th>
    
    <th>
      What the workload reaches
    </th>
  </tr>
</thead>

<tbody>
  <tr>
    <td>
      <code>
        mode: ENABLED
      </code>
      
      , <code>
        allowedHosts: []
      </code>
    </td>
    
    <td>
      Name resolution, nothing else — not even the api-gateway
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        mode: ENABLED
      </code>
      
      , <code>
        allowedHosts: [...]
      </code>
    </td>
    
    <td>
      Name resolution and the declared destinations
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        mode: DISABLED
      </code>
    </td>
    
    <td>
      Nothing — the guest gets no network interface at all
    </td>
  </tr>
</tbody>
</table>

An empty list means the same on a `BOXLITE` node, and hostnames are accepted on both providers.

`mode: DISABLED` leaves no interface to publish a port on or to reach the node's OTLP endpoint through, so a node refuses a workload declaring it together with `portMappings` or `telemetry` — on either provider — rather than starting one whose published ports or telemetry cannot work.

A node that does not deny egress by default cannot honour a declared allowlist, so a workload declaring one is refused there rather than started with access it was supposed to be denied.

A node denies every workload the cloud metadata endpoint (`169.254.169.254`) and the Azure WireServer's control ports (`168.63.129.16`), which is what keeps a guest from reading the host's own identity. A workload may still be granted one, by naming the address exactly:

<table>
<thead>
  <tr>
    <th>
      <code>
        allowedHosts
      </code>
      
       entry
    </th>
    
    <th>
      Reaches
    </th>
  </tr>
</thead>

<tbody>
  <tr>
    <td>
      <code>
        []
      </code>
    </td>
    
    <td>
      Name resolution and nothing else
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        ['10.0.1.0/24']
      </code>
    </td>
    
    <td>
      That range, minus what the node denies
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        ['registry.npmjs.org']
      </code>
    </td>
    
    <td>
      Whatever the node's resolver answers for that name, minus what the node denies
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        ['0.0.0.0/0']
      </code>
    </td>
    
    <td>
      Everything except the metadata endpoint
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        ['169.254.169.254/32']
      </code>
    </td>
    
    <td>
      The metadata endpoint, logged by name on the node
    </td>
  </tr>
</tbody>
</table>

An entry that names a protected address is placed above the node's own rules so it overrides them; every other entry is placed below, so a range containing one grants everything in it *except* that address, and a hostname resolving to one grants nothing there. Only a system participant can reach the service that carries a workload's policy, so naming an address outright is a decision the system made — a range that merely contains one is not, and does not become one by accident.

A provider needs its host prepared for it, so the setting follows the node's provisioning: a developer machine keeps the default, and a node built for Kata is configured for `CLOUD_HYPERVISOR`. An unrecognised value stops the vm-manager at startup rather than falling back, so a node never registers running something other than what it was configured for.

## Node capacity

Each vm-manager node reports its capacity when it registers with the orchestrator. A workload is placed on the first online node whose unallocated capacity covers the workload's `cpus`, `memoryMb`, and `diskSizeMb`, and that capacity is reserved on the node atomically, so two deployments placed at the same moment can never both take the same room; a deployment whose room was taken in between is placed again on what is left, up to three times.

`cpus` is in cores and may be a fraction: `0.5` is half a core, enforced as a CPU quota on the VM on `CLOUD_HYPERVISOR` nodes (a `BOXLITE` node boots the VM with the whole core the share is part of). The node's `freeCpus` moves by the same fraction, so a 4-core node holds eight half-core workloads.

The node's record keeps `freeCpus`, `freeMemoryMb` and `freeDiskMb`: the totals less what the workloads running on it are sized for. A run holds everything the workload is sized for from the moment it is placed; when it ends — stopped, exited, failed, or reported gone by the node — everything returns to the node. A run's end is recorded once, so a stop and the node's own report of the same end cannot return the room twice. A node that registers again rebuilds its free capacity from the workload records still running on it, so a run the node ended while the server was away is still accounted for; the write is conditional on the counters it overwrites, so a reservation or a release landing between the records' read and the write is answered by a rebuild run again on what is there.

The node keeps nothing of a run that has ended: once the server holds its outcome, the node removes the VM, its disk and its log files (after the shipper has read them). The workload's record stays on the server with its status and exit code, and its logs stay in the organization's log store under its id, until the record is deleted (see [Workload retention](#workload-retention)). A microservice restarted from the console gets a fresh VM from the project's current deployment.

<table>
<thead>
  <tr>
    <th>
      Reported
    </th>
    
    <th>
      Source on the node
    </th>
  </tr>
</thead>

<tbody>
  <tr>
    <td>
      <code>
        totalCpus
      </code>
    </td>
    
    <td>
      <code>
        os.cpus().length
      </code>
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        totalMemoryMb
      </code>
    </td>
    
    <td>
      <code>
        os.totalmem()
      </code>
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        totalDiskMb
      </code>
    </td>
    
    <td>
      Capacity of the filesystem the node's provider gives guest disks from: <code>
        BOXLITE_HOME
      </code>
      
       (default <code>
        ~/.boxlite
      </code>
      
      ) on <code>
        BOXLITE
      </code>
      
       nodes, the Docker data root on <code>
        CLOUD_HYPERVISOR
      </code>
      
       ones
    </td>
  </tr>
  
  <tr>
    <td>
      Capacity is reported in full, with nothing held back for the host OS, so size workloads with headroom for the node itself.
    </td>
    
    <td>
      
    </td>
  </tr>
</tbody>
</table>

## Node health

Some of what a node promises can stop being true while it runs, and each failure is silent: a data root that fell back to a filesystem without project quotas keeps running workloads and stops capping their disks, and a firewall chain that was flushed keeps running workloads and stops hiding the cloud metadata endpoint from them.

Each node re-checks these and sends what it can no longer guarantee with its heartbeat, so a node's liveness and its fitness arrive in one call. A node reporting problems reports itself `DRAINING`, so the orchestrator places no further workloads on it, and `ONLINE` once it reports none. The workloads already there keep running — a node that stopped enforcing a limit is unfit to take on more, not required to drop what it has.

`VmNode.state` carries the contract: `state.desired` is `{ phase: ONLINE }` from the moment the node registers, `state.observed` is what the node last reported, `ONLINE` or `DRAINING`, and `state.reconciled` is whether the two agree with nothing standing beside the node's word. `healthMessage` on the node is why it drains, or null when it is fit. Placement selects on `state.reconciled`, so a node takes workloads exactly when it reports itself fit, the orchestrator can reach it, and its removal has not been asked for; the reservation that follows the pick checks the same field in the shard operation that takes the room, so a node that left its desired state between the two grants nothing and placement picks again. The system console shows the reason on the node.

A node that loses its connection to the server — a server restart, a network partition — reconnects on its own and is reachable again with its next heartbeat. The reconcile master calls the node's worker whenever its next heartbeat would be overdue: a node silent past `heartbeatTimeoutSeconds` is marked with the condition `NODE_UNREACHABLE` on its `state.conditions`, whatever it reported last, and so is every workload whose run is still open on it — starting, running, or stopping. Every such mark, and every status a workload's run passes through, is entered in the `kinotic_watch_event` data stream with what caused it, and `VmNodeService.findHistory` and `WorkloadService.findHistory` read a node's or a workload's entries back, newest first; the system console shows them on the node's page and on the workload's History tab, and its **Change log** page lists every record's entries across the platform, newest first, through `WatchEventService.findAll`. The workload record keeps the last status the node reported and its room on the node, because a VM on the far side of a partition is still running; the node's next report of the workload is applied whatever it says, clearing the condition, and a report of a state earlier than the record's is otherwise ignored, since a run only moves forward: the write of a run's status declines one earlier than the record's in the same shard operation, so a write made from a read that a later write overtook leaves the run where it is. Only a heartbeat or a registration writes `lastSeen`; a state or allocation change leaves it as it is, so it never hides a heartbeat from the timeout, and the mark is conditional on the `lastSeen` the worker read, so a heartbeat landing between that read and the mark leaves the node unmarked. A call to the node's vm-manager that cannot be delivered is an earlier sign: the orchestrator checks the vm-manager's registration at once and, finding none, marks the node `NODE_UNREACHABLE` the same way, so nothing is placed on it while the heartbeat timeout runs; its next heartbeat clears the mark. A start or stop the vm-manager took and never answered leaves its workload marked `NODE_UNREACHABLE` too, since the node may have carried it out, and a microservice whose VM is in that state keeps `DEPLOYED` in the console with the reason beside it.

Deregistering a node is intent: `deregisterNode` refuses a reachable node that still runs workloads, and otherwise writes `state.deletionRequested`, so nothing more is placed on the node, and the node's worker finalizes it — every run still open on an unreachable node is recorded `FAILED`, which is a system user's word that the node is not coming back, and the record goes with its capacity ledger. Every workload names the deployment it belongs to in `state.parent`, so a run that ends, on any node, reaches the worker of that deployment.

<table>
<thead>
  <tr>
    <th>
      Provider
    </th>
    
    <th>
      What it re-checks
    </th>
  </tr>
</thead>

<tbody>
  <tr>
    <td>
      <code>
        CLOUD_HYPERVISOR
      </code>
    </td>
    
    <td>
      The container runtime answers, the data root still enforces project quotas, and the firewall still blocks the cloud metadata endpoint
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        BOXLITE
      </code>
    </td>
    
    <td>
      Nothing — boxlite carries its own guest kernel and filesystem, so it depends on nothing on the host that could quietly change
    </td>
  </tr>
</tbody>
</table>

A workload's `diskSizeMb` caps the guest rootfs, which is sparse and grows up to that cap. Host directories a workload mounts through `volumeMounts` are not part of it — their contents are bound from the host filesystem and are not counted against the node's disk.

On `BOXLITE` nodes a workload may declare at most one entry in `volumeMounts`: the node mounts a log directory into every workload's VM, and boxlite supports two mounts per VM in total, so a second declared mount is rejected when the workload is started. `CLOUD_HYPERVISOR` nodes impose no such limit and capture logs without a mount.

## Project deployment

The system server deploys a project on every push to its repository's default branch (see
[Push to Deploy](https://kinotic.ai/apps/deployment/push-to-deploy)). The pipeline runs a short-lived sync VM
per deployment and a short-lived SBOM VM when the project's dependencies changed, which connect to
the management server, and one long-lived runtime VM per microservice, which connects to the app server,
configured under `kinotic.systemApi.deployment.*`:

<table>
<thead>
  <tr>
    <th>
      Property
    </th>
    
    <th>
      Default
    </th>
    
    <th>
      Meaning
    </th>
  </tr>
</thead>

<tbody>
  <tr>
    <td>
      <code>
        managementServer.host
      </code>
    </td>
    
    <td>
      <strong>
        required
      </strong>
    </td>
    
    <td>
      Host the sync and SBOM workloads use to reach the management server (<code>
        KINOTIC_SERVER_HOST
      </code>
      
       in the guest), and the one destination their egress policy always permits. A server has no advertised address of its own, so startup fails without it. An IPv4 address or a hostname, on either provider
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        managementServer.port
      </code>
    </td>
    
    <td>
      <code>
        58503
      </code>
    </td>
    
    <td>
      Port the sync and SBOM workloads use to reach the management server
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        managementServer.useSsl
      </code>
    </td>
    
    <td>
      <code>
        false
      </code>
    </td>
    
    <td>
      Whether the sync and SBOM workloads reach the management server over TLS
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        appServer.host
      </code>
    </td>
    
    <td>
      <strong>
        required
      </strong>
    </td>
    
    <td>
      Host the runtime workloads use to reach the app server, and the one destination their egress policy always permits. An IPv4 address or a hostname, on either provider
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        appServer.port
      </code>
    </td>
    
    <td>
      <code>
        58505
      </code>
    </td>
    
    <td>
      Port the runtime workloads use to reach the app server
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        appServer.useSsl
      </code>
    </td>
    
    <td>
      <code>
        false
      </code>
    </td>
    
    <td>
      Whether the runtime workloads reach the app server over TLS
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        workloadRunnerImage
      </code>
    </td>
    
    <td>
      <code>
        kinoticai/workload-runner:latest
      </code>
    </td>
    
    <td>
      OCI image every deployment workload runs — the checkout/sync, UI publish and SBOM entrypoints and the microservice supervisor
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        syncAllowedHosts
      </code>
    </td>
    
    <td>
      <code>
        []
      </code>
    </td>
    
    <td>
      Destinations (IPv4 addresses, CIDRs, or hostnames) the sync workload may reach beyond the management server and the host of the repository's clone URL, which its egress policy always permits — typically <code>
        registry.npmjs.org
      </code>
      
      , so <code>
        bun install
      </code>
      
       works under default-deny egress
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        runtimeAllowedHosts
      </code>
    </td>
    
    <td>
      <code>
        []
      </code>
    </td>
    
    <td>
      Destinations (IPv4 addresses, CIDRs, or hostnames) the runtime workloads may reach beyond the app server
    </td>
  </tr>
</tbody>
</table>

The VMs are the same size in every environment; what differs between nodes is how many fit. The sync VM gets half a core, 2 GB of memory and a 2 GB root filesystem, where the package manager keeps its cache, with the project checkout mount (clone plus installs) capped at 4 GB; each runtime VM, and each site publish or removal VM and SBOM VM, gets half a core, 1 GB of memory and a 512 MB root filesystem — the checkout is a read-only mount, so the root holds only what the microservice writes. The disk caps are enforced where the node's filesystem carries project quotas.

The pipeline also requires the project's [repository host](#repository-host), which mints the
short-lived fetch token, and at least one online node advertising a workload data
directory — the project's checkout lives at `<workloadDataDir>/projects/<projectId>` on
the node that first deployed it, mounted read-write into the sync VM and read-only into
the runtime VMs.

## Repository host

`kinotic.managementApi.repoHost` names where each project's Git repository is hosted, and so
how a project is created, fetched by its sync workload, and pushed to:

- `GITHUB`, the default: the repository is created from the template
`kinotic.managementApi.github.repoTemplate` in the GitHub account the organization linked by
installing the system's GitHub App, and the App mints the fetch and write tokens.
- `GITEA`, which the `demonstration` profile sets: the management and system servers administer
a Gitea server (`deployment/docker-compose/compose.gitea.yml`). Each Kinotic organization is a
private Gitea organization of the same id, so nothing is linked. Each repository is created
holding one commit of the template, downloaded from GitHub and rendered for the project, and given
a push webhook to the management server's `/api/github/webhook`, signed with
`kinotic.managementApi.github.webhookSecret`; Gitea sends the same headers and signature GitHub
does. A repository is fetched through a Gitea account that may only read it and pushed to
through one that may only write it (`ProjectRepoCredentialService.issueWriteToken`, which a coding
agent calls as an MCP tool), each with
tokens good for an hour. Gitea names an organization in at most 40 characters and reserves some
names, among them `admin`, `api`, `explore`, `org` and `user`, so an organization whose id is longer
or reserved cannot create a project there: the creation fails with Gitea's reason.

`ProjectService.getRepositoryHost` tells the portal and coding agents which it is. Gitea has no
configuration of its own: the servers reach it at `kinotic-gitea:3000` on the compose network and
act as the `kinotic_admin` administrator `compose.gitea.yml` creates; a person's browser and Git
client reach it at `http://localhost:3001`, a deployment's sync workload at
`http://192.168.127.254:3001`, the host alias of a BOXLITE guest; and Gitea delivers its webhooks to
`kinotic-server-management:58503`.

## Application API hosts

Each application has an API host of its own, its `<organizationId>--<applicationId>` label
under a platform domain, and every UI a deployment builds is handed its application's host
to connect to (see [the UI build contract](https://kinotic.ai/apps/application-structure/applications-and-projects#the-ui-build-contract)).
The app server serves every application at its host: its login and OAuth routes take the
application from the host a request is addressed to and answer `404` on any other host, and it
signs an application's users in only from pages that are the application's own UIs. Each URL a
browser reaches is one setting, in the module every server that reads it loads. The application
hosts are `kinotic.domain.appApiBaseUrl`: the app server serves them, the system server hands each
UI build its application's host to connect to, and the management server builds the callback URLs
an organization registers with its own identity providers. The portal is `kinotic.domain.portalBaseUrl`:
where the management server's browser flows return and where the platform's emailed links open. The
management server's API is `kinotic.managementApi.apiBaseUrl`, read only by the servers that load
management-api. The Helm chart sets all three from the same paths in its values:

<table>
<thead>
  <tr>
    <th>
      Property
    </th>
    
    <th>
      Default
    </th>
    
    <th>
      Meaning
    </th>
  </tr>
</thead>

<tbody>
  <tr>
    <td>
      <code>
        kinotic.domain.appApiBaseUrl
      </code>
    </td>
    
    <td>
      —
    </td>
    
    <td>
      Scheme, domain and optional port every application's API host is a label under: with <code>
        https://apps-api.kinotic.io
      </code>
      
      , application <code>
        orders
      </code>
      
       of organization <code>
        acme
      </code>
      
       is served at <code>
        https://acme--orders.apps-api.kinotic.io
      </code>
      
      , which is also its OAuth issuer, the host its UIs are built against, and the host of the callback URL <code>
        OwnedOidcConfigurationService.findCallbackUrl
      </code>
      
       gives an application's or a tenant's OIDC configuration. The base URL itself is the shared host a sign-in through one of the system's social registrations returns to. The <code>
        demonstration
      </code>
      
       profile sets <code>
        http://localhost:58505
      </code>
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        kinotic.managementApi.apiBaseUrl
      </code>
    </td>
    
    <td>
      —
    </td>
    
    <td>
      The management server's API as a browser reaches it: the base of its OIDC redirect URIs, including the callback URL <code>
        findCallbackUrl
      </code>
      
       gives an organization's own OIDC configuration, and its OAuth issuer unless <code>
        kinotic.managementServer.issuerBaseUrl
      </code>
      
       is set. The management server's <code>
        e2e
      </code>
      
       and <code>
        demonstration
      </code>
      
       profiles set <code>
        http://localhost:58503
      </code>
      
      , its gateway port
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        kinotic.domain.portalBaseUrl
      </code>
    </td>
    
    <td>
      —
    </td>
    
    <td>
      The portal as a browser reaches it: where the management server's browser flows send the user, and the base of the verification, invite and sign-in links in the platform's emails, which every server that sends mail reads, unless the service sending one passes an <a href="https://kinotic.ai/platform/organization-management#email-templates">
        email template
      </a>
      
       naming its own UI. A sign-in, sign-up or invitation flow started from another UI the management server's <code>
        kinotic.cors.allowedOriginPattern
      </code>
      
       names, other than through <code>
        *
      </code>
      
      , returns to that UI instead. The <code>
        e2e
      </code>
      
       and <code>
        demonstration
      </code>
      
       profiles set <code>
        http://localhost:9090
      </code>
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        kinotic.cors.allowedOriginPattern
      </code>
    </td>
    
    <td>
      <code>
        http://localhost(:\d+)?
      </code>
    </td>
    
    <td>
      A regex of the origins a server's browser-facing routes accept CORS requests from; <code>
        *
      </code>
      
       admits every origin. The origins it names, other than through <code>
        *
      </code>
      
      , are also the only UIs the management server's browser flows return to, with their logins, social sign-up tokens, GitHub App install codes and errors, so name only origins you control.
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        kinotic.appServer.localUiOriginPattern
      </code>
    </td>
    
    <td>
      —
    </td>
    
    <td>
      Origins admitted as a UI of every application, for UIs a developer serves from their own machine; the <code>
        demonstration
      </code>
      
       profile sets <code>
        http:\/\/localhost:\d+
      </code>
      
      . Unset, an application's login is made only from its published sites, <code>
        <applicationId>--<uiName>.<organizationId>
      </code>
      
       under the sites domain
    </td>
  </tr>
</tbody>
</table>

## UI sites

A deployment of a commit that contains a UI publishes it into the platform's sites storage
account, one per environment, and it is served at its own hostname under the organization's
subdomain of the sites domain, `kinotic.domain.publishedSites.domain` below, by the Cloudflare sites
Worker. The account is configured under `kinotic.systemApi.uiDeployment.*`:

<table>
<thead>
  <tr>
    <th>
      Property
    </th>
    
    <th>
      Default
    </th>
    
    <th>
      Meaning
    </th>
  </tr>
</thead>

<tbody>
  <tr>
    <td>
      <code>
        siteStorage
      </code>
    </td>
    
    <td>
      <code>
        AZURE
      </code>
    </td>
    
    <td>
      Where published UIs are stored: <code>
        AZURE
      </code>
      
      , the sites storage account; <code>
        AZURITE
      </code>
      
      , the emulator a demonstration server runs (<a href="#site-storage-on-one-machine">
        site storage on one machine
      </a>
      
      ); or <code>
        DISABLED
      </code>
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        sitesStorageEndpoint
      </code>
    </td>
    
    <td>
      —
    </td>
    
    <td>
      Blob endpoint of the sites storage account terraform creates, where every published UI is written under <code>
        sites/<hostname>/
      </code>
      
      , as the publish and removal workloads reach it. Required
    </td>
  </tr>
</tbody>
</table>

The endpoint is validated at boot, like the GitHub App settings, so an environment whose site
storage is `DISABLED` still sets it, to a placeholder. In the Azure
deployment terraform creates the account and the Cloudflare side and passes the account and
domain to the server (see the [deployment guide](https://kinotic.ai/platform/deployment-guide)). The server never reads or writes the account itself: it holds Storage Blob
Data Contributor on it only to sign the URLs, scoped to one site's directory, that the publish
and removal workloads act through. The sites Worker reads the account over its blob endpoint
with a read-only SAS on the container, which terraform mints and binds to the Worker as a
secret; the container is private, so a site's files are reachable only through the Worker.

The sites domain, and the edge each organization's subdomain of it needs a DNS record at, are
configured under `kinotic.domain.publishedSites.*`. The platform creates the record when the
organization is created and again before its first site is published:

<table>
<thead>
  <tr>
    <th>
      Property
    </th>
    
    <th>
      Default
    </th>
    
    <th>
      Meaning
    </th>
  </tr>
</thead>

<tbody>
  <tr>
    <td>
      <code>
        domain
      </code>
    </td>
    
    <td>
      —
    </td>
    
    <td>
      The domain every organization's sites are under, e.g. <code>
        kinotic.io
      </code>
      
       or <code>
        dev.kinotic.io
      </code>
      
      : a site is <code>
        <applicationId>--<uiName>.<organizationId>.<domain>
      </code>
      
      . Required on the system server to publish a UI; an application's login admits pages under it
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        useSsl
      </code>
    </td>
    
    <td>
      <code>
        true
      </code>
    </td>
    
    <td>
      Whether a browser reaches the sites over TLS, which sets the scheme of each site's URL
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        port
      </code>
    </td>
    
    <td>
      —
    </td>
    
    <td>
      The port a browser reaches the sites on, in each site's URL; unset for the scheme's default port
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        cloudflare.enabled
      </code>
    </td>
    
    <td>
      <code>
        false
      </code>
    </td>
    
    <td>
      Whether the record is created in Cloudflare; <code>
        false
      </code>
      
       logs the subdomain and creates nothing, for an environment with no edge
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        cloudflare.zoneId
      </code>
    </td>
    
    <td>
      —
    </td>
    
    <td>
      The Cloudflare zone the sites domain is in
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        cloudflare.apiToken
      </code>
    </td>
    
    <td>
      —
    </td>
    
    <td>
      A token holding DNS edit on the zone and nothing else, imported as a system secret (<code>
        kinotic-cloudflare-dns-api-token
      </code>
      
      ), never written in a configuration file
    </td>
  </tr>
</tbody>
</table>

## Find-my-organizations challenge

The landing page's "Email me my organizations" form posts to a route open to anyone that sends an
email on every call, so the management server requires a Cloudflare Turnstile token with each
request and verifies it with the site's secret, under `kinotic.managementServer.turnstile.*`:

<table>
<thead>
  <tr>
    <th>
      Property
    </th>
    
    <th>
      Default
    </th>
    
    <th>
      Meaning
    </th>
  </tr>
</thead>

<tbody>
  <tr>
    <td>
      <code>
        enabled
      </code>
    </td>
    
    <td>
      <code>
        true
      </code>
    </td>
    
    <td>
      Whether a token is required; <code>
        false
      </code>
      
       accepts every request, for an environment with no Turnstile site such as the e2e stack or a demonstration server
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        siteKey
      </code>
    </td>
    
    <td>
      —
    </td>
    
    <td>
      The site key the portal renders the challenge with; public, passed by the cluster root as <code>
        turnstile_site_key
      </code>
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        secretKey
      </code>
    </td>
    
    <td>
      —
    </td>
    
    <td>
      The site's secret key, imported as a system secret (<code>
        kinotic-turnstile-secret-key
      </code>
      
      ), never written in a configuration file
    </td>
  </tr>
</tbody>
</table>

The `dev-server` profile uses Cloudflare's always-passing test site, so the form works with no site of
its own.

## LLM configurations

The large language models the system's services call are configured at runtime rather than in a
property file: a system user adds them on the system console's **System → LLM configurations**
page, and a service selects one by its name through `LlmConfigurationService.findByName`, published
in the `system-api` zone.

<table>
<thead>
  <tr>
    <th>
      Field
    </th>
    
    <th>
      Meaning
    </th>
  </tr>
</thead>

<tbody>
  <tr>
    <td>
      <code>
        name
      </code>
    </td>
    
    <td>
      The name a service selects the configuration by, unique in the system
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        provider
      </code>
    </td>
    
    <td>
      <code>
        ANTHROPIC
      </code>
      
       or <code>
        OPENAI
      </code>
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        model
      </code>
    </td>
    
    <td>
      The provider's id of the model, such as <code>
        claude-opus-5-5
      </code>
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        endpoint
      </code>
    </td>
    
    <td>
      The base URL of the provider's API, or none for the provider's public API
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        maxTokens
      </code>
      
      , <code>
        temperature
      </code>
    </td>
    
    <td>
      The options each request carries, or none for the defaults; <code>
        temperature
      </code>
      
       is from 0 to 1 for <code>
        ANTHROPIC
      </code>
      
       and from 0 to 2 for <code>
        OPENAI
      </code>
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        apiKeyHeld
      </code>
    </td>
    
    <td>
      Whether an API key is held, set by the server on every save
    </td>
  </tr>
</tbody>
</table>

The API key is given on save, `LlmConfigurationService.save(configuration, apiKey)`, and held through
`SecretStorageService` in the system's scope, keyed by the configuration's id; a save without one
keeps the key held, and deleting the configuration deletes its key. `findApiKey(id)` reads it back for
a system service that calls the model: only a cluster member calling as itself, with no participant
bound, can read it, so no gateway connection can, the system console's users included.

## Demonstration server

The `demonstration` profile runs the whole platform on one machine from `deployment/docker-compose`
(`compose.yml` runs every server and the migration with it, and `compose.sample-data.yml` runs the migration
again with `fixtures` for sample data), so Kinotic
can be tried, and developed, with no Azure, no GitHub and no mail service. Each server's
`application-demonstration.yml` holds the localhost URLs of the servers, Azurite and the local edge,
in-memory secret storage, generated system secrets, and no mail service. The UIs are served by
`kinotic-ui` (`compose.ui.yml`), the portal on `http://localhost:9090` and the system console on
`http://localhost:9091`. The management server's CORS pattern admits the portal, its vite dev server
on `5173`, and `http://localhost:9092`, where [Summit 42](https://github.com/kinotic-ai/summit-42)'s
own compose file serves its UI beside the stack; the system server's admits the console and its vite
dev server on `5174`. These settings make the demonstration server work:

<table>
<thead>
  <tr>
    <th>
      Property
    </th>
    
    <th>
      Default
    </th>
    
    <th>
      Meaning
    </th>
  </tr>
</thead>

<tbody>
  <tr>
    <td>
      <code>
        kinotic.domain.email.enabled
      </code>
    </td>
    
    <td>
      <code>
        true
      </code>
    </td>
    
    <td>
      <code>
        false
      </code>
      
       sends no email: each link a person must open is logged and returned to the UI that asked for it, which shows it. A sign-up's response carries its verification link, which the portal opens at once and an application's sign-up page shows, an inviter is shown each invitation's accept link to send on (<code>
        MemberService.findInvitationLink
      </code>
      
      ), and the landing page's find-my-organizations form lists the address's organizations. Nothing then proves an address belongs to the person using it, so email is disabled only on a server no one but the people trying it can reach, as the <code>
        demonstration
      </code>
      
       profile does
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        kinotic.managementServer.passwordSignUp
      </code>
    </td>
    
    <td>
      <code>
        false
      </code>
    </td>
    
    <td>
      Whether signing up with an email and a password is offered: the portal's sign-up page offers it (<code>
        /signup/email
      </code>
      
      ) in place of GitHub alone, and <code>
        POST /api/auth/org/signup
      </code>
      
       refuses it with 403 when unset
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        kinotic.managementServer.turnstile.enabled
      </code>
    </td>
    
    <td>
      <code>
        true
      </code>
    </td>
    
    <td>
      <code>
        false
      </code>
      
       on a demonstration server: the find-my-organizations form presents no Cloudflare Turnstile challenge, so the stack calls no Cloudflare service
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        kinotic.managementApi.repoHost
      </code>
    </td>
    
    <td>
      <code>
        GITHUB
      </code>
    </td>
    
    <td>
      <code>
        GITEA
      </code>
      
       on a demonstration server: projects' repositories are hosted on the Gitea <code>
        compose.gitea.yml
      </code>
      
       runs (see <a href="#repository-host">
        Repository host
      </a>
      
      )
    </td>
  </tr>
</tbody>
</table>

## Site storage on one machine

`kinotic.systemApi.uiDeployment.siteStorage` names what stands in for the sites storage account
where there is none:

- `AZURITE`, which the `demonstration` profile sets, uploads to the Azurite emulator that
`deployment/docker-compose/compose.sites.yml` runs, with `sitesStorageEndpoint` at
`http://192.168.127.254:10000/devstoreaccount1`, where a BOXLITE workload reaches the emulator.
The local edge runs the sites Worker over the emulator, so a published UI opens at
`http://<app>--<ui>.<org>.sites.kinotic.localhost:8088` (`kinotic.domain.publishedSites.domain: sites.kinotic.localhost`, `useSsl: false`, `port: 8088`). Each upload and removal URL names its site's directory and
carries a SAS for the whole `sites` container, signed with the emulator's well-known development
key, since the emulator honors no directory SAS. A site
reads ready as soon as its upload finished, since the edge reads the emulator on every request.
- `DISABLED`, which the tests and the e2e stack set, writes nothing and deletes nothing: every
published UI is marked ready at once with nothing serving it, and a removed UI's record is
deleted without a removal workload.

## Workload storage and log limits

A workload writes to two places the node must bound: the directories it mounts, and its own log output.

<table>
<thead>
  <tr>
    <th>
      Setting
    </th>
    
    <th>
      Bounds
    </th>
  </tr>
</thead>

<tbody>
  <tr>
    <td>
      <code>
        volumeMounts[].sizeLimitMb
      </code>
    </td>
    
    <td>
      What the guest may write through that mount. Unset leaves the mount bounded only by the host filesystem it comes from
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        logPolicy.maxSizeMb
      </code>
    </td>
    
    <td>
      Size at which the workload's current log file is rotated
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        logPolicy.maxFiles
      </code>
    </td>
    
    <td>
      How many rotated files are kept alongside the current one
    </td>
  </tr>
</tbody>
</table>

`sizeLimitMb` applies to writable mounts; a mount declared `readOnly` is already unwritable. It is enforced by an XFS project quota, so the host filesystem refuses the write once the cap is reached whatever the workload does — which requires the mount to live on an XFS filesystem mounted with `prjquota`, on a node running either provider. The two providers differ only in what a node that cannot enforce a cap does with it. A `CLOUD_HYPERVISOR` node is provisioned for quotas, so it refuses the workload and reports the condition on its heartbeat until it is fixed. A `BOXLITE` node may have no project quotas at all — on macOS it cannot have them — so it enforces the cap wherever the filesystem holding the mount carries them, exactly as a `CLOUD_HYPERVISOR` node does, and only runs a mount uncapped when that filesystem has none; it warns at startup when its workload data directory is on such a filesystem. A node that had project quotas and lost them reports it on its heartbeat and stops taking workloads, whichever provider it runs. A workload's logs occupy at most `maxSizeMb * (maxFiles + 1)`, and the limits are applied by the VM provider rather than by the workload, so a workload cannot raise its own ceiling.

## Workload retention

A workload's record is the only link to its logs, so nothing deletes one as a side effect: destroying a workload removes its VM and its disk from the node and records the run `STOPPED`, and the record and its logs stay. Deleting a workload, from the console or through `WorkloadOrchestrationService.deleteWorkload`, is the one way either goes: it deletes every log line the workload wrote from the organization's Loki tenant (the system's own, `kinotic-system`, for a system workload), then the record, and refuses a run still open on its node — stop or destroy it first. The retention sweep, one cluster singleton beside the reconcile master, runs hourly and deletes the records of runs that ended longer ago than the retention period the same way, a page of a hundred at a time, up to fifty pages a sweep; what one sweep leaves, the next one takes. An operator runs the same sweep on demand as the `workload-retention-sweep` system job, with **Run system job** on the system console's **Jobs** page (see [System jobs](https://kinotic.ai/platform/reference/grind-jobs#system-jobs)).

Configured under `kinotic.systemApi.workload.*`:

<table>
<thead>
  <tr>
    <th>
      Property
    </th>
    
    <th>
      Default
    </th>
    
    <th>
      Meaning
    </th>
  </tr>
</thead>

<tbody>
  <tr>
    <td>
      <code>
        retentionDays
      </code>
    </td>
    
    <td>
      <code>
        30
      </code>
    </td>
    
    <td>
      How long the record of a run that has ended is kept, with its logs, before the sweep deletes it
    </td>
  </tr>
</tbody>
</table>

The Loki deletion API needs the compactor's retention and a delete request store, which `values-loki.yaml` and `values-loki-azure.yaml` (the object store on Azure) configure, as does the compose file; without them a delete fails and the record stays for the next sweep. Loki's queriers filter a deleted range out of query results once they load the request, at their first query for the tenant or their five-minute reload, at a cost on every query in the tenant until its compactor applies the request, which the configured cancel period of one hour bounds; the sweep asks once per organization for every workload in a page, so a busy platform adds one pending request per organization per hour.

## Trace logging

Trace logging prints every STOMP frame the gateway reads and writes, every service invocation the
invoker dispatches, and the arguments each invoked method received. That is what makes it useful
during development and what makes a high-frequency service — a heartbeat, a poll — drown out
everything else in the log.

`kinotic.traceLog` names the CRIs that stay out of it, and the ones that stay in:

```yaml
kinotic:
  traceLog:
    excludes:
      - srv://system-api~org.kinotic.system.api.services.workload.VmNodeOrchestrationService/*
      - srv://system-api~org.kinotic.system.api.services.KinoticClusterInfoService/findClusterInfo
```

Each entry is matched against the fully qualified CRI with Ant wildcards — `*` matches within one
segment, `**` across segments — so a trailing `/*` covers every method of a service and a raw CRI
covers just that one method. A scoped invocation carries its scope in the CRI, so a pattern that
must cover one needs the scope wildcard as well:
`srv://*@system-api~com.acme.NodeService/*`.

An include wins over an exclude that also matches, which turns the filter around: exclude
everything and name what is worth watching.

```yaml
kinotic:
  traceLog:
    excludes:
      - '**'
    includes:
      - srv://app.acme-org.orders-app~com.acme.OrderService/**
```

Patterns are matched against every destination but one. A reply comes back on a destination
belonging to the client connection rather than naming what was invoked, so it is never matched —
an excluded request is marked as it enters the gateway and its reply is dropped from the log along
with it.
An excluded CRI therefore drops the whole exchange: the request frame, the invocation and its
arguments, the values a streaming result emits, and the reply frame the client receives. The
patterns are consulted only while trace logging is enabled, and cost nothing at any other level.

`kinotic.traceLog` is what a node starts with. The system console's **Logging** dialog, on each
node in the Cluster page's server node table, whichever of the three servers it runs, edits the
patterns on a running node the same way it sets log levels — see
[Observability](https://kinotic.ai/platform/observability#application-logs). Those edits last until the node restarts,
which returns it to its configured `kinotic.traceLog`.


## Sitemap

See the full [sitemap](https://kinotic.ai/sitemap.md) for all pages.
