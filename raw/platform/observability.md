# Observability

> Monitoring, tracing, and logging across your Kinotic applications.

## Overview

Kinotic provides deep observability across your applications, aggregated at multiple levels: System, Organization, and Application.

## Metrics

Real-time monitoring of CPU, memory, and data throughput for all running services. Metrics are collected automatically for every deployed application and available through the Kinotic dashboard.

## Traces and Spans

Drill from high-level overviews into detailed execution logs. Distributed tracing follows requests across service boundaries, so you can pinpoint performance bottlenecks and errors in complex service interactions. A workload running in a micro VM ships its traces and metrics through the node that runs it — see [Workload Traces and Metrics](#workload-traces-and-metrics) — and the portal shows them on an application's **Observability** page, and across the organization at **Observability** in the organization sidebar.

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

Every node runs a managed [Grafana Alloy](https://grafana.com/docs/alloy/latest/) process whose pipeline is regenerated as workloads come and go; Alloy tails each running workload's log files and pushes them to Loki, and receives the traces and metrics of the workloads that elect telemetry and pushes them to Tempo and Mimir (see [Workload Traces and Metrics](#workload-traces-and-metrics)). What a workload has to do for its logs to be shipped depends on the node's [VM provider](/platform/configuration#vm-provider):

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
      Write log files into that directory itself. The node names it as <code>
        KINOTIC_LOG_DIR
      </code>
      
       in the guest environment, with the <code>
        logPolicy
      </code>
      
       as <code>
        KINOTIC_LOG_MAX_SIZE_MB
      </code>
      
       and <code>
        KINOTIC_LOG_MAX_FILES
      </code>
      
      ; the workload-runner image honours this, writing a size-rotated <code>
        workload.log
      </code>
      
       there
    </td>
  </tr>
</tbody>
</table>

`CLOUD_HYPERVISOR` nodes label each stream `stdout` or `stderr`. Both providers bound what a workload's logs occupy on the node through `logPolicy` — `maxSizeMb` is the size at which the current file rotates, and `maxFiles` how many rotated files are kept beside it — the container runtime enforcing it on `CLOUD_HYPERVISOR`, and the image itself on `BOXLITE`.

Workload VMs run detached from the vm-manager process by default (`Workload.detached`), and the vm-manager persists each workload's state on the node. If the vm-manager restarts (a crash, a systemd restart), it reattaches to the detached VMs that are still running and regenerates the Alloy pipeline, so their logs keep shipping. A non-detached workload runs in the foreground — the call that starts it resolves only once its run has ended, with the exit code — and ends with the vm-manager process.

On a deployment's job run page the **Sync project source** step expands into the sync workload's log, the way a CI step does: open while the step runs, tailing live, and readable afterwards from the run's history. A workload whose run has ended keeps its log files on the node until it is destroyed, and a destroy waits for the shipper to read them to the end first, so a run over in seconds still ships every line it wrote.

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

## Workload Traces and Metrics

A workload that sets `telemetry` to `true` is given an OTLP endpoint of its own on the node it runs on, and the traces and metrics it exports there are shipped to Tempo and Mimir under its organization's tenant, carrying the same identity as its log streams. Telemetry is elected per workload: the node ships nothing for a workload that leaves it off, whatever the workload's runtime exports. The runtime workload of every [deployed project](/platform/configuration#project-deployment) elects it, under the project's name as its service name.

### What the node does

When the workload's VM is created the node issues it an endpoint — a host port its Alloy listens on for that workload alone, and a bearer token only that guest is given — and names it in the guest environment through the standard OpenTelemetry variables, which every OpenTelemetry SDK reads:

<table>
<thead>
  <tr>
    <th>
      Variable
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
        OTEL_EXPORTER_OTLP_ENDPOINT
      </code>
    </td>
    
    <td>
      <code>
        http://<the node, as the guest reaches it>:<port>
      </code>
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        OTEL_EXPORTER_OTLP_PROTOCOL
      </code>
    </td>
    
    <td>
      <code>
        http/protobuf
      </code>
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        OTEL_EXPORTER_OTLP_HEADERS
      </code>
    </td>
    
    <td>
      <code>
        authorization=Bearer%20<token>
      </code>
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        OTEL_TRACES_EXPORTER
      </code>
    </td>
    
    <td>
      <code>
        otlp
      </code>
      
       where the node ships traces (<code>
        KINOTIC_TEMPO_URL
      </code>
      
      ), else <code>
        none
      </code>
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        OTEL_METRICS_EXPORTER
      </code>
    </td>
    
    <td>
      <code>
        otlp
      </code>
      
       where the node ships metrics (<code>
        KINOTIC_MIMIR_URL
      </code>
      
      ), else <code>
        none
      </code>
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        OTEL_LOGS_EXPORTER
      </code>
    </td>
    
    <td>
      <code>
        none
      </code>
      
       — logs ship through the files above
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        OTEL_SERVICE_NAME
      </code>
    </td>
    
    <td>
      The workload's name, unless the workload's own <code>
        environment
      </code>
      
       sets it
    </td>
  </tr>
</tbody>
</table>

The exporter variables are laid over the workload's own `environment` and `secrets`, so a workload cannot point its exporter elsewhere by setting them itself; the service name is the one variable the workload may set, since it is how its spans and metrics are grouped. The endpoint holds for the workload's life, restarts included, and is released when the workload is destroyed. Each receiver accepts only its own token, so a guest cannot push into another workload's stream. A workload electing telemetry on a node that ships neither signal runs without an endpoint.

### What the workload must do

Export over OTLP from that environment. An OpenTelemetry SDK configured from the environment — the Java agent, or the Node SDK with its default configuration — does so without any code of its own. The Kinotic runtimes instrument every service invocation through the OpenTelemetry API, so a Kinotic application whose process runs such an SDK ships a span per call, continuing the trace of the caller that invoked it. A runtime that ignores the variables ships nothing.

A workload with `network.mode` `DISABLED` has no way to reach the endpoint, so a node refuses one that also elects telemetry rather than starting it silent.

### How a guest reaches its endpoint

<table>
<thead>
  <tr>
    <th>
      Provider
    </th>
    
    <th>
      The guest reaches the node at
    </th>
    
    <th>
      What the node does
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
      The docker bridge gateway (<code>
        172.17.0.1
      </code>
      
       unless the daemon is configured otherwise)
    </td>
    
    <td>
      Binds the receiver to that address alone and opens the workload's port to the workload's address in the host firewall, above the floor that shields the node's own services from every guest
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        BOXLITE
      </code>
    </td>
    
    <td>
      boxlite's host alias <code>
        192.168.127.254
      </code>
      
      , whose proxy completes the connection over the host's loopback
    </td>
    
    <td>
      Binds the receiver to loopback and adds the alias to the workload's egress allowlist. boxlite's allowlist matches hosts rather than ports, so a workload holding an endpoint on a <code>
        BOXLITE
      </code>
      
       node can reach every loopback service of the node, not only its receiver
    </td>
  </tr>
</tbody>
</table>

The port is opened by the same per-workload rules as the workload's [egress](/platform/configuration#workload-egress), so a `CLOUD_HYPERVISOR` node that does not deny egress by default refuses a workload electing telemetry, as it refuses one declaring `allowedHosts`.

### Identity and tenant

Every span and every metric arrives with these resource attributes, matching the labels on the workload's log streams:

<table>
<thead>
  <tr>
    <th>
      Attribute
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

Traces and metrics are pushed with `X-Scope-OrgID` set to the organization id, or `kinotic-system` for platform workloads with no organization — the same tenant the logs go to. Tempo and Mimir run multi-tenant (`multitenancy_enabled: true`, as the docker-compose stack sets them), so each organization's telemetry is kept apart the way Loki keeps its logs, and Tempo's metrics-generator writes the span metrics it derives — `traces_spanmetrics_calls_total`, `traces_spanmetrics_latency_bucket` — into the same tenant, carrying `application_id` and `workload_id` beside `service` so one application's RED metrics can be selected within its organization.

### Reading workload traces and metrics

The `TelemetryService` (`@kinotic-ai/management-api`, `Kinotic.telemetry`) searches traces (`searchTraces`, a TraceQL query over a time range), fetches one trace with all its spans (`findTrace`), and evaluates PromQL (`queryMetrics`, a range query at a step). Each call names an organization: an organization participant may name only its own, and a system participant any, or none for the platform's own telemetry. The tenant is the boundary; what a query selects within it is the caller's to decide. All three return the raw Tempo and Prometheus response bytes for the caller to parse.

The portal shows them on an application's **Observability** page: a trace search — by service, span name, errors, and duration — opening each trace on its own page as a waterfall of its spans with their attributes, and the requests, errors, and latency of the application's services beside a free PromQL query. The organization's **Observability** page has the same view across all of its applications or one of them, and so does the system console at **Organizations → (organization) → Observability**, with each application's view under **Applications → (application) → Observability**. The console's own **Observability** page, under Platform, reads the system tenant: the traces and metrics of the servers, the gateway, and the worker nodes themselves.

## Configuration

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
        kinotic.managementApi.lokiUrl
      </code>
      
       / <code>
        KINOTIC_MANAGEMENTAPI_LOKIURL
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
      Loki HTTP API the node's Alloy pushes logs to; unset disables log shipping
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        kinotic.managementApi.tempoUrl
      </code>
      
       / <code>
        KINOTIC_MANAGEMENTAPI_TEMPOURL
      </code>
      
       (server)
    </td>
    
    <td>
      Tempo HTTP API the server queries (default <code>
        http://localhost:3200
      </code>
      
      )
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        kinotic.managementApi.mimirUrl
      </code>
      
       / <code>
        KINOTIC_MANAGEMENTAPI_MIMIRURL
      </code>
      
       (server)
    </td>
    
    <td>
      Mimir HTTP API the server queries, whose Prometheus API is under <code>
        /prometheus
      </code>
      
       (default <code>
        http://localhost:9009
      </code>
      
      )
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        KINOTIC_TEMPO_URL
      </code>
      
       (vm-manager)
    </td>
    
    <td>
      Base URL of the OTLP/HTTP endpoint the node's Alloy pushes traces to — Tempo's own (<code>
        http://tempo:4318
      </code>
      
      ), or a collector in front of it; unset disables trace shipping
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        KINOTIC_MIMIR_URL
      </code>
      
       (vm-manager)
    </td>
    
    <td>
      Base URL of the OTLP/HTTP endpoint the node's Alloy pushes metrics to — Mimir's own (<code>
        http://mimir:9009/otlp
      </code>
      
      ), or a collector in front of it; unset disables metric shipping
    </td>
  </tr>
</tbody>
</table>

The vm-manager resolves the Alloy binary from the `PATH`, downloading its pinned release when none is found. Both the download and Alloy's launch happen while the node starts up, before it registers and accepts workloads, so no workload operation waits on them. One Alloy process ships logs, traces, and metrics; a node configured for some of them runs it for those alone.
