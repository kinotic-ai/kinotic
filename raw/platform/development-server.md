# Development Server

> A development environment for a few peers on one physical host, what it keeps in Azure, and how their applications move to Kinotic Cloud.

## Overview

This page is the design record and setup plan for the development server: a development
environment on one physical host — a Ryzen 9 with 96 GB of RAM and four SSDs — plus two Intel
NUCs that run the peers' workloads, where a few peers build Kinotic applications against the
real platform: organization sign-up,
push-to-deploy into micro VMs, published UIs served through Front Door, email, logs and traces
in the portal. Every service runs on the host. Azure keeps what a single host cannot provide
(Front Door, the sites storage account, Communication Services email, the DNS zone), what the
migration path needs (a Key Vault, a snapshot container), and the portal and the system
console, static files served by the same Front Door as the published sites.

When Kinotic Cloud opens, the peers' organizations, applications, and data move there and keep
working. The migration is a designed step of this environment rather than an afterthought, and
two of the decisions below exist only because of it.

<callout type="info">

**Status.** The host, the Azure root and the nine containers are built and the portal is up;
the nodes, the first system user and the end-to-end deployment are still to come. This page
records the decisions, the target topology, and the steps that build it, in the order they
have to happen.

</callout>

<development-server-diagram>



</development-server-diagram>

## Decisions

### Proxmox VE: a container per service; the nodes are their own machines

The platform and a vm-manager node cannot share one operating system. The node's provisioning
(`deployment/vm-node/setup-node.sh`) owns `/etc/docker/daemon.json`,
`br_netfilter`, and the `DOCKER-USER` chain, and its firewall floor (`kinotic-node-firewall`)
drops every guest packet addressed to the node itself — so a gateway on the node's own address
is unreachable from every workload, and the kit's README says outright not to colocate the
api-gateway with workloads. So the nodes are separate machines: two Intel NUCs (32 GB, 250 GB
each) running the kit on their own KVM, and the host runs no workloads at all. Two nodes also
exercise placement, heartbeat expiry, and draining, which one node never does. If capacity
outgrows the NUCs, a node VM on the host is the fallback: nested KVM is the configuration every
measurement in the Cloud Hypervisor evaluation was taken in (Azure `Standard_D4s_v3`).

Proxmox over plain Ubuntu with libvirt because it adds ZFS, snapshots, containers created from
OCI images, and the `bpg/proxmox` Terraform provider, so the host can be a third terraform
root beside `kind/` and `azure/`. Incus is the credible alternative for an all-code, no-UI
host; XCP-ng is out because Xen's nested virtualization is off the path the vm-manager was
tested on; Harvester wants three nodes.

Every other service is a Proxmox container of its own, created from the image the compose
stack pulls — Proxmox VE 9.1 creates LXC containers from OCI images — rather than a VM
running docker-compose. Each service is then a first-class guest: its own address, its own
CPU and memory limits, its own mounts and startup order, visible and restartable in the
Proxmox UI, sharing the host's kernel with no VM in between. There is no compose, no
Kubernetes, and no second layer of orchestration inside a guest. What the Proxmox API does
not take for such a container — an environment with Elasticsearch's dotted setting names,
which it validates as words, a resolv.conf, which it writes into no OCI image, the console
log, and the ownership of the mounted directories, which must be the container's user's
before the image is unpacked over them — a script terraform uploads applies on the host from
a manifest per container, and the same script is where the operator's secrets are merged in,
so no secret passes through terraform. Proxmox also does not restart a container whose
entrypoint exits, so a host timer does.

A node is never a container on the host: it needs its own kernel modules, sysctls, and iptables.

### No Kubernetes

Nothing the server needs comes from Kubernetes. Ignite discovers peers by shared filesystem or
static addresses as readily as by the Kubernetes API (`KinoticIgniteConfig`), platform secrets
are two JSON files at a path, TLS is two PEM files at a path, and the docker-compose stack in
`deployment/docker-compose/` is the complete platform — with Tempo and Mimir, which the Helm
charts do not deploy yet. The development server runs that stack's images and config files,
one container each. The Helm charts stay the production artifact; KinD on a laptop is where
they are rehearsed. If the host ever needs to rehearse them too, a k3s VM slots in without
touching anything else.

### Three Elasticsearch nodes, one physical disk each, one shard and one replica

On one host, a node is a failure domain only if its disk is. Redundancy is therefore the
conjunction of replicas across nodes **and** one physical disk per node: two nodes whose data
directories share a disk, or two volumes carved from one pool, replicate a shard onto the same
failure domain and lose it together. Each node's data directory is a dataset on a ZFS pool
that spans exactly one disk (`/es1/data`, `/es2/data`, `/es3/data`), bind-mounted into that
node's container, never a volume from a shared pool.

Three nodes rather than two because of quorum, not storage: with two master-eligible nodes the
cluster stops accepting writes the moment one disk dies, even though the surviving replica holds
every shard. With three, one disk can fail and the cluster stays writable.

One shard and one replica per index. The production defaults are three and two
(`PersistenceProperties`), and every published EntityDefinition costs `shards × (1 + replicas)`
against the 1000-shard-per-node ceiling, so the production numbers on three small nodes would
cap the peers' definitions early for no gain.

### Elasticsearch on a private network, with security off

The three nodes attach only to a private network: a Proxmox SDN simple zone, a bridge with no
physical port, whose subnet the host gateways and source-NATs. The migration and the server's
second interface are on it; nothing on the LAN or the internet has a route to it. With that
isolation the cluster runs the way the local compose stack does, `xpack.security.enabled: false`: no transport TLS to issue and rotate, no passwords to place. The NAT is for one
direction only — the nodes reach the snapshot container in Azure.

### The node runs the Cloud Hypervisor provider

`BOXLITE` is the provider that runs anywhere a developer works; `CLOUD_HYPERVISOR` is the one a
node is provisioned for, with a health contract the node re-checks on every heartbeat, egress
denied by default, host-side stdout capture, and no mount limit (see
[VM provider](/platform/configuration#vm-provider)). The development server is a production
shape, so the node is provisioned with the kit under `deployment/vm-node` exactly as any other
node is.

### Azure keeps Front Door, the sites account, email, DNS, and a server Key Vault

The developer root `deployment/terraform/azure/dev` already builds everything a server off Azure
needs to publish UIs and send email: a resource group, the sites module under
`apps-<environment>.<zone>`, a Key Vault holding the wildcard certificate Front Door reads, and
a service principal whose `AZURE_CLIENT_ID`, `AZURE_CLIENT_SECRET`, and `AZURE_TENANT_ID``DefaultAzureCredential` takes before anything else. A `dev-server` root is that root plus
four things: a Key Vault for the server, the portal and the system console on the sites
module's Front Door, DNS rights for certificate issuance, and a container for Elasticsearch
snapshots.

The server's Key Vault is kept deliberately. Without it the secret storage backend is in-memory
(`SecretStorageConfiguration`): nothing stored survives a restart and nothing can be carried to
the cloud. Nothing calls `SecretStorageService` today, so the cost is nil this week; the first
feature that stores a peer's secret would turn it into silent data loss. With the `AZURE`
backend the peers' secrets are already in Azure on migration day.

### Migrations become append-only from the first peer sign-up

Index mappings are strict, `V1__init.sql` is edited in place while the version is `-SNAPSHOT`,
and a restored `migration_history` says every migration is applied — so a field added to a table
between the development server's launch and cutover fails the first save of that entity on the
cloud side. The repository rule starts append-only discipline "when the first release exists";
for every table holding a peer's data, that launch is the release. From then on a change to one
of those tables is a new versioned file. Tables no peer writes (`kinotic_vm_node`,
`kinotic_workload`, the fixtures) can keep being edited in place.

### The same GitHub App as Kinotic Cloud

`kinotic_github_app_installation.githubInstallationId` belongs to one App (the `appId` in
`kinotic-server/src/main/resources/application.yml`), and one App has one webhook URL. Using
the cloud's App for the development server means the installation rows migrate untouched and
cutover is a change of webhook URL; a separate App would mean every peer re-installs on their
repos after migration. The cost is that while the development server owns the webhook URL,
nothing else receives pushes from that App.

### Backups are Elasticsearch snapshots to Azure Blob, and they are the migration vehicle

Replicas across disks protect against a disk. They do nothing for a dead host, a power supply,
or a bad migration. A snapshot repository of type `azure` (the module ships with ES 9) with a
daily SLM policy is the backup, and restoring the latest snapshot into the cloud cluster is how
the peers' data gets there. `deployment/terraform/azure/PRODUCTION.md` lists ES snapshots to
Azure Blob as an open item for the cloud; the development server builds it first.

## Host

Four 512 GB NVMe drives, one per M.2 slot: Proxmox on its own, and one per Elasticsearch
node. Each ES drive is a ZFS pool of its own, so a disk failure is contained to the node that
owns it, and `smartd` on the host watches each drive.

<table>
<thead>
  <tr>
    <th>
      Drive
    </th>
    
    <th>
      Holds
    </th>
  </tr>
</thead>

<tbody>
  <tr>
    <td>
      1
    </td>
    
    <td>
      Proxmox, every container's root filesystem, the Loki/Tempo/Mimir/Grafana data under <code>
        /var/lib/kinotic/data
      </code>
    </td>
  </tr>
  
  <tr>
    <td>
      2
    </td>
    
    <td>
      ZFS pool <code>
        es1
      </code>
      
      , dataset <code>
        /es1/data
      </code>
      
      , mounted into <code>
        es-1
      </code>
    </td>
  </tr>
  
  <tr>
    <td>
      3
    </td>
    
    <td>
      ZFS pool <code>
        es2
      </code>
      
      , dataset <code>
        /es2/data
      </code>
      
      , mounted into <code>
        es-2
      </code>
    </td>
  </tr>
  
  <tr>
    <td>
      4
    </td>
    
    <td>
      ZFS pool <code>
        es3
      </code>
      
      , dataset <code>
        /es3/data
      </code>
      
      , mounted into <code>
        es-3
      </code>
    </td>
  </tr>
</tbody>
</table>

Four is the smallest count that gives every ES node its own disk and Proxmox its own. Three
works, with `es-3` on a partition of the Proxmox drive, since that is still a different device
from the other two nodes; the cost is rebuilding `es-3` from its replicas whenever the boot
drive is replaced. 512 GB is enough everywhere: an ES node holds about 430 GB at the
allocation watermark, and the Proxmox drive's largest tenant is the stores' retention.

<table>
<thead>
  <tr>
    <th>
      Guest
    </th>
    
    <th>
      Kind
    </th>
    
    <th>
      vCPU
    </th>
    
    <th>
      RAM
    </th>
    
    <th>
      Network
    </th>
  </tr>
</thead>

<tbody>
  <tr>
    <td>
      <code>
        es-1
      </code>
      
      , <code>
        es-2
      </code>
      
      , <code>
        es-3
      </code>
    </td>
    
    <td>
      container
    </td>
    
    <td>
      2 each
    </td>
    
    <td>
      4 GB each (2 GB heap)
    </td>
    
    <td>
      private
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        kinotic-server
      </code>
    </td>
    
    <td>
      container
    </td>
    
    <td>
      4
    </td>
    
    <td>
      4 GB
    </td>
    
    <td>
      LAN + private
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        kinotic-migration
      </code>
    </td>
    
    <td>
      container, runs once
    </td>
    
    <td>
      2
    </td>
    
    <td>
      2 GB
    </td>
    
    <td>
      private
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        loki
      </code>
      
      , <code>
        tempo
      </code>
      
      , <code>
        mimir
      </code>
      
      , <code>
        grafana
      </code>
    </td>
    
    <td>
      containers
    </td>
    
    <td>
      2, 2, 2, 1
    </td>
    
    <td>
      1, 1, 2, 0.5 GB
    </td>
    
    <td>
      LAN
    </td>
  </tr>
  
  <tr>
    <td>
      Proxmox
    </td>
    
    <td>
      —
    </td>
    
    <td>
      —
    </td>
    
    <td>
      the rest, ~70 GB
    </td>
    
    <td>
      ZFS ARC, and headroom to grow the ES heaps
    </td>
  </tr>
</tbody>
</table>

The stores have static addresses on `vmbr0`; the server's LAN interface takes its address by
DHCP under a fixed MAC, because the ISP's router forwards by device and reserves the
device's address when the forward is saved. The private network is `10.10.0.0/24` with the
host at `.1`. Only `kinotic-server` is reachable from the internet, on two forwarded
ports.

Proxmox itself is installed by hand, and `host/prepare-host.sh` runs once on it: the three ZFS
pools, the directories, `vm.max_map_count`, the keepalive and address-updater timers, and the
host's exposure, SSH by key alone and a firewall that admits SSH and the web UI from the LAN
only. From there the host is a
terraform root, `deployment/terraform/proxmox`, using `bpg/proxmox`: the private network, the
seven images, the containers with their mounts, and the manifests and config files it uploads
to the host. Everything it uploads comes from this
repository; nothing secret passes through it; its README is the runbook.

## Containers

```text
deployment/terraform/proxmox/
  main.tf                            # the network, images, containers, and the applier run
  host/prepare-host.sh               # once on the host: ZFS pools, directories, sysctl, keepalive timer
  host/kinotic-apply-container.py    # on the host after every apply: environment, config files, ownership
  generate-secrets.sh                # the JWT key set, the master key, Grafana's password
  sync-secrets.sh                    # copies them to the host and re-applies the containers that read them
deployment/docker-compose/
  tempo.yml, mimir.yml, grafana-*.yaml, dashboards/   # the stores' config files, addresses substituted
kinotic-server/src/main/resources/
  application-dev-server.yml         # the dev-server profile: what is fixed for this shape
/etc/kinotic/secrets/                # on the host, placed by hand
  kinotic-server.env                 # AZURE_CLIENT_SECRET, merged into the server's environment
  kinotic-server/                    # mounted at /etc/kinotic in the server: secrets.yml, platform-secrets/, certs/
  grafana.env                        # the admin password
/var/lib/kinotic/{config,data}/<service>, /es{1,2,3}/data   # each container's files and state
```

The server runs with `SPRING_PROFILES_ACTIVE=production,dev-server`. `production` is what
takes it off the development conveniences: no auto-seeded platform secrets
(`DevPlatformSecretsGenerator` is development-only), the halting Ignite failure handler, the
production shard defaults — which `dev-server` then overrides. Its environment is composed by
terraform from the compose service's, the Azure root's outputs, and the addresses only the
proxmox root knows; its secrets arrive through `secrets.yml`, which the profile imports, and
the env file the applier merges.

### Elasticsearch

Three containers from the same image, identical except for name, address and mount:

<table>
<thead>
  <tr>
    <th>
      Setting
    </th>
    
    <th>
      Value
    </th>
    
    <th>
      Why
    </th>
  </tr>
</thead>

<tbody>
  <tr>
    <td>
      <code>
        node.roles
      </code>
    </td>
    
    <td>
      <code>
        master,data,ingest,transform
      </code>
    </td>
    
    <td>
      Three master-eligible nodes give quorum through one disk loss; a dedicated master is wasted on one host
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        discovery.seed_hosts
      </code>
      
      , <code>
        cluster.initial_master_nodes
      </code>
    </td>
    
    <td>
      the three private addresses, the three names
    </td>
    
    <td>
      Cluster bootstrap
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        xpack.security.enabled
      </code>
    </td>
    
    <td>
      <code>
        false
      </code>
    </td>
    
    <td>
      The private network is the isolation; see the decision above
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        ES_JAVA_OPTS
      </code>
    </td>
    
    <td>
      <code>
        -Xms2048m -Xmx2048m
      </code>
    </td>
    
    <td>
      Half of each container's 4 GB
    </td>
  </tr>
  
  <tr>
    <td>
      data mount
    </td>
    
    <td>
      <code>
        /esN/data
      </code>
      
       → <code>
        /usr/share/elasticsearch/data
      </code>
    </td>
    
    <td>
      One ZFS pool per node, one disk per pool
    </td>
  </tr>
  
  <tr>
    <td>
      LAN
    </td>
    
    <td>
      none
    </td>
    
    <td>
      The server, the migration, and the host reach the cluster on the private network; 9200 is never on the LAN
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        vm.max_map_count
      </code>
    </td>
    
    <td>
      <code>
        262144
      </code>
      
       on the host
    </td>
    
    <td>
      Containers share the host kernel; ES refuses to start without it
    </td>
  </tr>
</tbody>
</table>

Server side, in `application-dev-server.yml` and the environment:

<table>
<thead>
  <tr>
    <th>
      Property
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
        kinotic.domain.elasticConnections[0..2]
      </code>
    </td>
    
    <td>
      <code>
        10.10.0.11
      </code>
      
      , <code>
        .12
      </code>
      
      , <code>
        .13
      </code>
      
       on 9200, <code>
        http
      </code>
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        kinotic.persistence.numberOfShards
      </code>
      
       / <code>
        numberOfReplicas
      </code>
    </td>
    
    <td>
      <code>
        1
      </code>
      
       / <code>
        1
      </code>
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        KINOTIC_MIGRATION_ELASTIC_HOST
      </code>
    </td>
    
    <td>
      <code>
        10.10.0.11
      </code>
      
      , for the one-shot migration container
    </td>
  </tr>
</tbody>
</table>

The snapshot repository is registered once ES is up: an `azure` repository named for the
environment, its account key in each node's keystore, and an SLM policy taking a daily
snapshot of every index with 30 days' retention.

### Server

<table>
<thead>
  <tr>
    <th>
      Property
    </th>
    
    <th>
      Value
    </th>
    
    <th>
      Why
    </th>
  </tr>
</thead>

<tbody>
  <tr>
    <td>
      <code>
        kinotic.domain.appBaseUrl
      </code>
    </td>
    
    <td>
      <code>
        https://dev-portal.kinotic.ai
      </code>
    </td>
    
    <td>
      The portal, served by Front Door from the sites account
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        kinotic.domain.apiBaseUrl
      </code>
    </td>
    
    <td>
      <code>
        https://dev-api.kinotic.ai
      </code>
    </td>
    
    <td>
      REST, STOMP, MCP, and the OIDC redirect URIs; <code>
        issuerBaseUrl
      </code>
      
       falls back to it
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        kinotic.apiGateway.stompPort
      </code>
    </td>
    
    <td>
      <code>
        443
      </code>
    </td>
    
    <td>
      The api-gateway port is the public one: a consumer router forwards a port to the same port only, and an LXC autodev hook lowers the container's unprivileged port floor so the server's user binds it
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        kinotic.apiGateway.webServer.enabled
      </code>
    </td>
    
    <td>
      <code>
        false
      </code>
    </td>
    
    <td>
      Nothing is served from the jar's webroot; the API port is the only one exposed, as in the cluster
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        kinotic.apiGateway.ssl.enabled
      </code>
      
       / <code>
        certPath
      </code>
      
       / <code>
        keyPath
      </code>
    </td>
    
    <td>
      <code>
        true
      </code>
      
      , the two certbot PEMs
    </td>
    
    <td>
      Vert.x terminates TLS, no reverse proxy, matching KinD and Azure
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        kinotic.platformSecrets.jwtSigningKeysPath
      </code>
    </td>
    
    <td>
      <code>
        /etc/kinotic/platform-secrets/jwt-signing-keys
      </code>
    </td>
    
    <td>
      Same JSON shape <code>
        deployment/kind/terraform/platform-secrets.tf
      </code>
      
       generates: <code>
        activeKeyId
      </code>
      
       plus a <code>
        keys
      </code>
      
       list
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        kinotic.domain.secretStorage.masterKey
      </code>
    </td>
    
    <td>
      from <code>
        secrets.yml
      </code>
    </td>
    
    <td>
      <code>
        SecretNameDeriver
      </code>
      
       derives every stored secret's name from it, so it is generated once and carried to the cloud
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        kinotic.domain.secretStorage.backend
      </code>
      
       / <code>
        azure.vaultUrl
      </code>
    </td>
    
    <td>
      <code>
        AZURE
      </code>
      
      , the development server's vault
    </td>
    
    <td>
      The Key Vault decision above
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        kinotic.domain.email.enabled
      </code>
      
       / <code>
        endpoint
      </code>
      
       / <code>
        senderAddress
      </code>
    </td>
    
    <td>
      <code>
        true
      </code>
      
      , the global ACS endpoint, <code>
        DoNotReply@kinotic.ai
      </code>
    </td>
    
    <td>
      Sign-up and invite mail; the principal holds Contributor on the email service
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        kinotic.systemApi.uiDeployment.disableProvisioner
      </code>
      
       / <code>
        sitesDomain
      </code>
      
       / <code>
        sitesStorageEndpoint
      </code>
    </td>
    
    <td>
      <code>
        false
      </code>
      
      , <code>
        apps-dev.kinotic.ai
      </code>
      
      , the development server's sites account
    </td>
    
    <td>
      The <code>
        dev-server
      </code>
      
       root's outputs, as the <code>
        dev
      </code>
      
       root emits them
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        kinotic.systemApi.deployment.serverHost
      </code>
      
       / <code>
        serverPort
      </code>
      
       / <code>
        serverUseSsl
      </code>
    </td>
    
    <td>
      the server's LAN IPv4, <code>
        443
      </code>
      
      , <code>
        true
      </code>
    </td>
    
    <td>
      Workloads on the nodes reach the gateway across the LAN; a <code>
        CLOUD_HYPERVISOR
      </code>
      
       node rejects a hostname here. The guest trusts the Let's Encrypt chain
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        kinotic.systemApi.deployment.syncAllowedHosts
      </code>
    </td>
    
    <td>
      GitHub's and the npm registry's published address ranges, as CIDRs
    </td>
    
    <td>
      Egress rules match addresses, not names; the development value uses names because it runs <code>
        BOXLITE
      </code>
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        kinotic.apiGateway.cors.allowedOriginPattern
      </code>
      
       / <code>
        allowCredentials
      </code>
    </td>
    
    <td>
      the portal, the console, and <code>
        https://[a-z0-9-]+\.apps-dev\.kinotic\.ai
      </code>
      
      ; <code>
        true
      </code>
    </td>
    
    <td>
      The <code>
        kubernetes
      </code>
      
       profile hardcodes <code>
        kinotic.ai
      </code>
      
       and is not active here
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        kinotic.managementApi.github.appPrivateKey
      </code>
      
       / <code>
        webhookSecret
      </code>
    </td>
    
    <td>
      from <code>
        secrets.yml
      </code>
    </td>
    
    <td>
      The cloud's App; its webhook URL points at <code>
        https://dev-api.kinotic.ai/api/github/webhook
      </code>
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        AZURE_CLIENT_ID
      </code>
      
       / <code>
        AZURE_TENANT_ID
      </code>
      
      , <code>
        AZURE_CLIENT_SECRET
      </code>
    </td>
    
    <td>
      the Azure root's outputs; <code>
        kinotic-server.env
      </code>
    </td>
    
    <td>
      The <code>
        dev-server
      </code>
      
       root's service principal
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        OTEL_EXPORTER_OTLP_{TRACES,METRICS,LOGS}_ENDPOINT
      </code>
      
      , <code>
        OTEL_EXPORTER_OTLP_HEADERS
      </code>
    </td>
    
    <td>
      Tempo's <code>
        :4318
      </code>
      
      , Mimir's <code>
        :9009/otlp
      </code>
      
      , Loki's <code>
        :3100/otlp
      </code>
      
      ; <code>
        X-Scope-OrgID=kinotic-system
      </code>
    </td>
    
    <td>
      No collector: the agent exports each signal to its store under the platform tenant, which is what the compose collector stamps on
    </td>
  </tr>
</tbody>
</table>

### Loki, Tempo, Mimir, Grafana

Four containers on the LAN, running the config files from `deployment/docker-compose` with
the compose service names replaced by the containers' addresses — one definition of each
store for local development and the development server. Loki's 3100, Tempo's 4318, and
Mimir's 9009 are what the node's Alloy ships to; Grafana on 3000 asks for a login, since the
LAN reaches it. Their data lives under `/var/lib/kinotic/data`, so a container replaced for a
newer image keeps it.

### TLS

certbot on the host with the `dns-azure` plugin, authenticating as the same service principal,
which the `dev-server` root grants DNS Zone Contributor on `kinotic.ai`. One certificate for
`dev-api.kinotic.ai`. The deploy hook installs the PEMs into the secrets directory the
server's container mounts and reboots the container, which is the whole of what Reloader does
in the cluster. The portal's and the console's certificates are Front Door's own, managed.

## Nodes

Two Intel NUCs, 32 GB and 250 GB each, Ubuntu 22.04 (what the kit is verified on) installed
with two XFS partitions mounted with `prjquota`: `/var/lib/docker`, which `setup-node.sh` keeps
instead of creating its 40 GB loop image, and `/var/lib/kinotic/workloads`. Then the kit from
`deployment/vm-node`:

```bash
sudo ./setup-node.sh            # docker, kata 4.1.0 on cloud-hypervisor, daemon.json, firewall floor
sudo touch /etc/kinotic/egress-default-deny && sudo systemctl restart kinotic-node-firewall
sudo ./install-vm-manager.sh    # bun, @kinotic-ai/vm-manager, kinotic-vm-manager.service
sudo ./verify-node.sh           # every invariant, again after every reboot
```

The Azure IMDS and WireServer drops install and verify unchanged; they protect nothing here and
are left in so every node is provisioned by one path. A NUC's 32 GB is roughly a dozen runtime
VMs at 1 GB beside a couple of 2 GB sync VMs (`DeploymentProperties`); a first deployment lands
on the first `ONLINE` node with room for it.

The vm-manager runs as root under `kinotic-vm-manager.service` with Bun (root for iptables, the
Docker socket, and project quotas). Its environment is `/etc/kinotic/vm-manager.env`: the
proxmox root's `vm_manager_env` output, which carries the server's and the stores' addresses,
plus the node's own id. The machine credentials go in `/etc/kinotic/vm-manager.secrets.env`,
which the service waits for:

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
        KINOTIC_VM_PROVIDER
      </code>
    </td>
    
    <td>
      <code>
        CLOUD_HYPERVISOR
      </code>
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        KINOTIC_NODE_ID
      </code>
    </td>
    
    <td>
      <code>
        dev-node-1
      </code>
      
      , <code>
        dev-node-2
      </code>
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        KINOTIC_SERVER_HOST
      </code>
      
       / <code>
        KINOTIC_SERVER_PORT
      </code>
      
       / <code>
        KINOTIC_SERVER_USE_SSL
      </code>
    </td>
    
    <td>
      the server's LAN IPv4, <code>
        443
      </code>
      
      , <code>
        true
      </code>
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        KINOTIC_CLIENT_ID
      </code>
      
       / <code>
        KINOTIC_CLIENT_SECRET
      </code>
    </td>
    
    <td>
      a SYSTEM-scope machine created in the system console; client credentials are the non-Azure machine path
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        KINOTIC_WORKLOAD_DATA_DIR
      </code>
    </td>
    
    <td>
      <code>
        /var/lib/kinotic/workloads
      </code>
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        KINOTIC_WORKLOAD_DNS
      </code>
    </td>
    
    <td>
      the LAN resolver, permitted on port 53 for every workload
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        KINOTIC_LOKI_URL
      </code>
      
       / <code>
        KINOTIC_TEMPO_URL
      </code>
      
       / <code>
        KINOTIC_MIMIR_URL
      </code>
    </td>
    
    <td>
      <code>
        http://<loki>:3100
      </code>
      
      , <code>
        http://<tempo>:4318
      </code>
      
      , <code>
        http://<mimir>:9009/otlp
      </code>
    </td>
  </tr>
</tbody>
</table>

## Azure: the `dev-server` terraform root

`deployment/terraform/azure/dev-server` shares `modules/dev-environment` with the `dev/` root
a developer uses for their own machine — the resource group, the sites module, the sites key
vault, and the service principal — and adds what a server peers depend on needs. Local state,
`environment = "dev"`, creating:

<table>
<thead>
  <tr>
    <th>
      Resource
    </th>
    
    <th>
      Purpose
    </th>
  </tr>
</thead>

<tbody>
  <tr>
    <td>
      Resource group <code>
        rg-kinotic-dev
      </code>
    </td>
    
    <td>
      Everything below but the principal
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        modules/sites
      </code>
      
       under <code>
        apps-dev.<zone>
      </code>
    </td>
    
    <td>
      Front Door profile, endpoint, wildcard domain and certificate, the sites storage account
    </td>
  </tr>
  
  <tr>
    <td>
      Key Vault <code>
        kv-kinotic-dev-sites
      </code>
    </td>
    
    <td>
      The wildcard certificate Front Door reads, as in <code>
        dev/
      </code>
    </td>
  </tr>
  
  <tr>
    <td>
      Key Vault <code>
        kv-kinotic-dev
      </code>
    </td>
    
    <td>
      The server's secret storage; the principal holds Key Vault Secrets Officer, as <code>
        cluster/keyvault.tf
      </code>
      
       grants the workload identity
    </td>
  </tr>
  
  <tr>
    <td>
      Storage account <code>
        stkinoticdevsnapshots
      </code>
    </td>
    
    <td>
      The ES snapshot repository's container
    </td>
  </tr>
  
  <tr>
    <td>
      Front Door domains <code>
        dev-portal.<zone>
      </code>
      
      , <code>
        dev-console.<zone>
      </code>
    </td>
    
    <td>
      The portal and the system console, served from <code>
        sites/<hostname>/
      </code>
      
       in the sites account by the route and rule set every published site uses, on managed certificates; <code>
        deploy-ui.sh
      </code>
      
       builds both with <code>
        --mode dev-server
      </code>
      
       and uploads them
    </td>
  </tr>
  
  <tr>
    <td>
      DNS <code>
        dev-api.<zone>
      </code>
      
       A record
    </td>
    
    <td>
      The host's public address; <code>
        kinotic-dyndns.timer
      </code>
      
       on the host rewrites it when the ISP changes the address
    </td>
  </tr>
  
  <tr>
    <td>
      Service principal <code>
        kinotic-dev-server
      </code>
    </td>
    
    <td>
      Storage Blob Data Contributor on the sites account, Contributor on the email service, Key Vault Secrets Officer on the server vault, DNS Zone Contributor on the zone for certbot and the address updater
    </td>
  </tr>
</tbody>
</table>

Its `dev_server_env` output is the non-secret half of the server's environment, which the
proxmox root merges into the server container's; its `secrets_env` output is the principal's
secret, which the operator places on the host in `kinotic-server.env` by hand.

## Network and access

The router forwards one port to the server container, 443, on which the server itself
listens (REST, STOMP, MCP, the GitHub webhook), so `https://dev-api.kinotic.ai` is the API
with no port in the URL. The ISP's router forwards a port to the same port only and names
its targets by device, so the server's MAC carries a name in the router's device list. The
portal at `https://dev-portal.kinotic.ai` and the system console at
`https://dev-console.kinotic.ai` are static files in the sites account that Front Door serves
the way it serves every published site, so the host runs nothing for them and the router
forwards nothing to them. This is the cloud's layout, a hosted SPA against an API host, with
no reverse proxy on the host. The router's address is dynamic: a timer on the host rewrites
the API's A record whenever it changes, as the server's principal.

Peers use `https://dev-portal.kinotic.ai`: organization sign-up with email verification
through ACS, social login through the platform OIDC providers registered with
`https://dev-api.kinotic.ai` redirect URIs, the CLI's device grant, and MCP hosts through the
authorization-code grant. Nothing on the LAN besides the forwarded port is reachable from
outside.

## Backups

<table>
<thead>
  <tr>
    <th>
      What
    </th>
    
    <th>
      How
    </th>
    
    <th>
      Restore drill
    </th>
  </tr>
</thead>

<tbody>
  <tr>
    <td>
      Elasticsearch
    </td>
    
    <td>
      SLM daily snapshot to the Azure Blob repository, 30 days kept
    </td>
    
    <td>
      Restore the latest snapshot into a scratch three-node cluster on the host, once, before the first peer signs up, and again before cutover
    </td>
  </tr>
  
  <tr>
    <td>
      Container root filesystems
    </td>
    
    <td>
      none
    </td>
    
    <td>
      Recreated from the images; every container's state is in a host directory
    </td>
  </tr>
  
  <tr>
    <td>
      Secrets (<code>
        /etc/kinotic/secrets
      </code>
      
      )
    </td>
    
    <td>
      The generated directory, kept off the host
    </td>
    
    <td>
      <code>
        sync-secrets.sh
      </code>
    </td>
  </tr>
  
  <tr>
    <td>
      Nodes
    </td>
    
    <td>
      none
    </td>
    
    <td>
      Ubuntu and the kit again; the checkouts are redeployable
    </td>
  </tr>
  
  <tr>
    <td>
      Workload checkouts
    </td>
    
    <td>
      none
    </td>
    
    <td>
      Redeploy from GitHub
    </td>
  </tr>
  
  <tr>
    <td>
      Logs, traces, metrics
    </td>
    
    <td>
      none
    </td>
    
    <td>
      Accepted loss
    </td>
  </tr>
</tbody>
</table>

## Migration to Kinotic Cloud

Everything a peer has lives in one of these places, and moves as follows:

<table>
<thead>
  <tr>
    <th>
      State
    </th>
    
    <th>
      Lives in
    </th>
    
    <th>
      Moves by
    </th>
  </tr>
</thead>

<tbody>
  <tr>
    <td>
      Organizations, identities, credentials, OIDC configurations, applications, projects, entity definitions, named queries, refresh tokens, GitHub installations, job runs
    </td>
    
    <td>
      os-data indices (<code>
        V1__init.sql
      </code>
      
      )
    </td>
    
    <td>
      The last snapshot, restored into the cloud cluster
    </td>
  </tr>
  
  <tr>
    <td>
      Customer entity data
    </td>
    
    <td>
      entity indices
    </td>
    
    <td>
      The same snapshot
    </td>
  </tr>
  
  <tr>
    <td>
      Access tokens in flight
    </td>
    
    <td>
      Signed with the development server's <code>
        jwt-signing-keys
      </code>
    </td>
    
    <td>
      That key is added to the cloud key set as a non-active entry; <code>
        KinoticJwtIssuer
      </code>
      
       verifies by <code>
        kid
      </code>
      
       against every key in the set, so tokens it issued verify until they expire
    </td>
  </tr>
  
  <tr>
    <td>
      Refresh tokens
    </td>
    
    <td>
      <code>
        kinotic_refresh_token
      </code>
      
       rows, opaque and hashed
    </td>
    
    <td>
      Restored with the snapshot; they do not depend on the signing key
    </td>
  </tr>
  
  <tr>
    <td>
      Stored secrets
    </td>
    
    <td>
      The development server's Key Vault
    </td>
    
    <td>
      Copied to the cluster vault, or the cluster is pointed at this vault until it is empty
    </td>
  </tr>
  
  <tr>
    <td>
      Published UIs
    </td>
    
    <td>
      The development server's sites account under <code>
        sites/<label>.apps-dev.kinotic.ai/
      </code>
    </td>
    
    <td>
      Each project is redeployed after cutover; the URL changes to <code>
        <label>.apps.kinotic.ai
      </code>
    </td>
  </tr>
  
  <tr>
    <td>
      Deployments and checkouts
    </td>
    
    <td>
      <code>
        kinotic_project_deployment.nodeId
      </code>
      
       and <code>
        hostDir
      </code>
      
      , the node's disk
    </td>
    
    <td>
      Scrubbed, then redeployed
    </td>
  </tr>
  
  <tr>
    <td>
      Sessions, logs, traces, metrics
    </td>
    
    <td>
      Ignite, Loki, Tempo, Mimir
    </td>
    
    <td>
      Not migrated
    </td>
  </tr>
</tbody>
</table>

The cutover, in order:

1. Announce a freeze to the peers; take a final snapshot; stop the development server.
2. Restore the snapshot into the cloud os-data and entity clusters. Both clusters must be at the
development server's migration version or carry only appended migrations beyond it.
3. Delete the restored `kinotic_vm_node` and `kinotic_workload` rows and clear `nodeId`,
`hostDir`, `syncWorkloadId`, and `uiPublishWorkloadId` on every `kinotic_project_deployment`
row. `resolveTarget` reuses an existing deployment's node without checking it exists, so a row
still pointing at the development node would fail its next deployment. A re-home operation
is the durable version of this step.
4. Add the development server's signing key to the cloud `jwt-signing-keys` set, inactive; drop
it a day later.
5. Point the GitHub App's webhook URL at the cloud gateway.
6. Re-run the deployment of every project, which places it on a cloud node and republishes its
UIs under `apps.kinotic.ai`.
7. Change `dev-api.kinotic.ai` to a CNAME of the cloud gateway so the peers' CLI logins and
project configuration keep resolving, and stop the address updater; they sign in again on
`portal.kinotic.ai`, since sessions are `__Host-` cookies and the social-login redirect
URIs are registered per host.
8. Disable the development node's machine identity; keep the development server running
read-only for a week, then tear it down.

## Developers' own environments

The same pieces serve a developer's machine, without the Proxmox host: the compose stack
from `deployment/docker-compose` with the `BOXLITE` vm-manager beside it, and the `dev/` root
for Front Door, the sites account, and email, as the contributing guide describes. Every
development environment shares one GitHub App. An App has one webhook URL, and it points at
the development server, so a developer's own server sees no push events: deployments there
start from the re-run path, and a developer who needs the webhook registers an App of their
own. Sign-in through Azure and Google is outside this page until that support is defined.

## Build order

1. **Azure root.** `deployment/terraform/azure/dev-server`; apply.
2. **Host.** Proxmox installed by hand on disk 0; `host/prepare-host.sh` with the three
Elasticsearch disks.
3. **Secrets and the certificate.** `generate-secrets.sh`, the Azure secret and the GitHub
App's key filled in, `sync-secrets.sh`; certbot on the host.
4. **The fleet.** `deployment/terraform/proxmox`; apply. The applier brings up the cluster,
the stores, runs the migration to completion and verifies it, and starts the server.
Register the snapshot repository and SLM policy; `deploy-ui.sh` for the two UIs; confirm
sign-up mail arrives and the portal loads on `https://dev-portal.kinotic.ai`.
5. **Nodes.** Ubuntu 22.04 and the kit on each NUC, `vm-manager.env` from the terraform
output plus the node's id, the SYSTEM machine's credentials from the system console in
`vm-manager.secrets.env`; confirm each node is `ONLINE` with no health message.
6. **End to end.** Deploy the template project from a peer's organization: the sync VM fetches
through the allowlist, the runtime VM registers its microservice, the UI appears at
`<label>.apps-dev.kinotic.ai`, and the run's log and the microservice's traces show in the
portal.
7. **Restore drill.** Restore the previous night's snapshot into a scratch cluster and open the
portal against it. Repeat before cutover.

## Open items

- A first system user. The migration runs with the `production` profile, so no fixture user
exists, and system users otherwise arrive through Entra SSO, which the development server
does not run. The system console, and with it the SYSTEM machine the vm-manager connects as
in step 5, needs a bootstrap for a first system user.
- Containers from OCI images are a technology preview in Proxmox VE 9.1. The environment,
the resolvers and the console log are applied on the host until the API takes them, and a
container whose entrypoint exits is restarted by a host timer; both fold into the terraform
root as Proxmox catches up.
- Loki, Tempo, and Mimir are on the LAN without authentication, because the nodes' Alloy ships
to them from the LAN. A VLAN holding the host and the nodes is the change to make if the LAN
is not trusted.
- The compose stack runs Elasticsearch 9.5.1 and the ECK values pin 9.0.2. The cloud cluster
must be at or above the development server's version on restore day.
- Tempo and Mimir are in compose and not in Helm. When the charts gain them nothing changes on
the development server; until then the cloud has no trace or metric history to migrate into,
which is accepted.
- Whether `dev-api.kinotic.ai` stays as a CNAME indefinitely or is retired after the peers have
moved their configuration.
- The one-time scrub in cutover step 3 versus building the re-home operation before cutover.
- Whether the node's IPv4-only egress allowlist for GitHub and npm is maintained by hand from
their published ranges or by a resolver-backed ipset, which the kit's README names as the path
for hostname allowlists.
