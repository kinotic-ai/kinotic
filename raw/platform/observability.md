# Observability

> Monitoring, tracing, and logging across your Kinotic applications.

## Overview

Kinotic provides deep observability across your applications, aggregated at multiple levels: System, Organization, and Application.

## Metrics

Real-time monitoring of CPU, memory, and data throughput for all running services. Metrics are collected automatically for every deployed application and available through the Kinotic dashboard.

## Traces and Spans

Drill from high-level overviews into detailed execution logs. Distributed tracing follows requests across service boundaries, so you can pinpoint performance bottlenecks and errors in complex service interactions.

## LLM Observability

Trace user interactions with LLMs and track token utilization for cost analysis. LLM request and response data is indexed via Grafana Loki, giving you full-text search across all LLM interactions with filtering by user, application, model, and time range.

## Audit Logs

Track platform activity including:

- **Login history** — Who connected, when, and from where
- **Configuration changes** — OIDC provider updates, LLM configuration changes, and application settings modifications
- **Activity counts** — Aggregate usage metrics per user, application, and organization

## Application Logs

View microservice logs directly from the dashboard with the ability to temporarily adjust logging levels for debugging. Increase verbosity on a running service to investigate an issue, then restore normal levels when done — no redeployment required.

The same dialog edits a node's [trace log filters](/platform/configuration#trace-logging), so turning a logger up to TRACE does not have to mean drowning in whatever service talks most. Both changes last until the node restarts.

## Workload Logs

Logs from micro VM workloads (builds, deploys, and application containers) are shipped to Grafana Loki and can be tailed live or queried historically, per workload.

### Log shipping architecture

Every node runs a managed [Grafana Alloy](https://grafana.com/docs/alloy/latest/) process whose pipeline is regenerated as workloads come and go; Alloy tails each running workload's log files and pushes them to Loki. What a workload has to do to be shipped depends on the node's [VM provider](/platform/configuration#vm-provider):

<table>
<thead>
  <tr>
    <th>
      Provider
    </th>
    
    <th>
      What is shipped
    </th>
    
    <th>
      What the workload must do
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
      The workload's stdout and stderr, captured by the container runtime
    </td>
    
    <td>
      Nothing — write to stdout and stderr
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        BOXLITE
      </code>
    </td>
    
    <td>
      Any <code>
        *.log
      </code>
      
       file under <code>
        /var/log/kinotic
      </code>
      
      , a per-workload host directory mounted into the VM
    </td>
    
    <td>
      Write log files into that directory itself
    </td>
  </tr>
</tbody>
</table>

`CLOUD_HYPERVISOR` nodes label each stream `stdout` or `stderr`, and bound what a workload's logs occupy on the node through `logPolicy` — `maxSizeMb` is the size at which the current file rotates, and `maxFiles` how many rotated files are kept beside it.

Workload VMs run detached from the vm-manager process by default (`Workload.detached`), and the vm-manager persists each workload's state on the node. If the vm-manager restarts (a crash, a systemd restart), it reattaches to the detached VMs that are still running and regenerates the Alloy pipeline, so their logs keep shipping. A non-detached workload runs in the foreground — the call that starts it resolves only once its run has ended, with the exit code — and ends with the vm-manager process.

A stopped workload can be restarted in place (`restartWorkload`) unless it was stopped with `Workload.autoRemove`, which discards the VM and its disk at stop. A restart boots the same VM, so its log streams continue under the same `vm_id` label.

Every log stream carries these labels:

<table>
<thead>
  <tr>
    <th>
      Label
    </th>
    
    <th>
      Value
    </th>
  </tr>
</thead>

<tbody>
  <tr>
    <td>
      <code>
        workload_id
      </code>
    </td>
    
    <td>
      The workload's id
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        vm_id
      </code>
    </td>
    
    <td>
      The provider's id for the micro VM
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        stream
      </code>
    </td>
    
    <td>
      <code>
        stdout
      </code>
      
       or <code>
        stderr
      </code>
      
      , on <code>
        CLOUD_HYPERVISOR
      </code>
      
       nodes
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        node_id
      </code>
    </td>
    
    <td>
      The vm-manager node the workload runs on
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        application_id
      </code>
    </td>
    
    <td>
      The workload's application, when it has one
    </td>
  </tr>
</tbody>
</table>

Loki runs multi-tenant. A workload's logs are stored in its organization's tenant (`X-Scope-OrgID` = the organization id), so one organization's queries can never see another's streams. Platform workloads with no organization ship to the reserved `kinotic-system` tenant — organization ids beginning with `kinotic` are reserved for the platform. Platform operators can query across tenants with pipe-separated ids (for example `acme|kinotic-system`).

### Reading workload logs

The `LogService` (`@kinotic-ai/management-api`) streams (`tail`) and queries (`history`) the logs of workloads the caller may view: an organization participant sees its own organization's workloads, a system participant sees any. Both methods return raw Loki response bytes for the caller to parse.

### Configuration

<table>
<thead>
  <tr>
    <th>
      Setting
    </th>
    
    <th>
      Description
    </th>
  </tr>
</thead>

<tbody>
  <tr>
    <td>
      <code>
        kinotic.managementApi.loki.url
      </code>
      
       / <code>
        KINOTIC_MANAGEMENTAPI_LOKI_URL
      </code>
      
       (server)
    </td>
    
    <td>
      Loki HTTP API the server queries (default <code>
        http://localhost:3100
      </code>
      
      )
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        KINOTIC_LOKI_URL
      </code>
      
       (vm-manager)
    </td>
    
    <td>
      Loki HTTP API the node's Alloy pushes to; unset disables log shipping
    </td>
  </tr>
</tbody>
</table>

The vm-manager resolves the Alloy binary from the `PATH`, downloading its pinned release when none is found. Both the download and Alloy's launch happen while the node starts up, before it registers and accepts workloads, so no workload operation waits on them.
