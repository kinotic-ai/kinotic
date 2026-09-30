# Deployment Guide

> Deploying and operating Kinotic OS in various environments.

<alert type="info">

Detailed deployment guide coming soon.

</alert>

## Overview

Kinotic OS deploys to Kubernetes and provides several deployment configurations for different environments.

## Deployment Options

### Helm Charts

Production-ready Helm charts are available in `deployment/helm/` for deploying:

- **Kinotic servers** — `deployment/helm/kinotic`: the org, system and app servers in one release, each a Deployment, Service and ConfigMap of its own with its replicas, resources, service type and TLS certificate, and the migration Job that precedes them. Every server's pods join one headless Service, through which Ignite discovers its peers, so the three form one cluster, and each server signs tokens with a JWT key set of its own, `<server>-jwt-signing-keys`
- **Elasticsearch** — Search and persistence cluster
- **Load Generator** — For performance testing

Each server mounts a platform-secrets volume, projected from the global Key Vault on Azure and
from a Secret the KinD terraform creates on KinD. It holds the server's JWT key set and the
secrets the server imports as properties when it starts, each file named after the property it
sets:

<table>
<thead>
  <tr>
    <th>
      File
    </th>
    
    <th>
      Key Vault secret, or key of the KinD Secret
    </th>
    
    <th>
      Mounted on
    </th>
  </tr>
</thead>

<tbody>
  <tr>
    <td>
      <code>
        jwt-signing-keys
      </code>
    </td>
    
    <td>
      <code>
        <server>-jwt-signing-keys
      </code>
    </td>
    
    <td>
      each server, its own key set
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        kinotic.domain.secretStorage.masterKey
      </code>
    </td>
    
    <td>
      <code>
        kinotic-secret-storage-master-key
      </code>
    </td>
    
    <td>
      every server
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        kinotic.managementApi.github.appPrivateKey
      </code>
    </td>
    
    <td>
      <code>
        kinotic-github-app-private-key
      </code>
    </td>
    
    <td>
      the servers with <code>
        githubApp
      </code>
      
       set: the org and system servers, which load <code>
        management-api
      </code>
    </td>
  </tr>
  
  <tr>
    <td>
      <code>
        kinotic.managementApi.github.webhookSecret
      </code>
    </td>
    
    <td>
      <code>
        kinotic-github-webhook-secret
      </code>
    </td>
    
    <td>
      the same
    </td>
  </tr>
</tbody>
</table>

A server picks up a rotated JWT key set while it runs, and an imported secret when it restarts.
On Azure the global terraform seeds the master key and takes the GitHub App's key and webhook
secret from `TF_VAR_github_app_private_key` and `TF_VAR_github_webhook_secret`. KinD's Secret
holds placeholders for the App's secrets, which the servers boot with, so a call to GitHub fails
there until a real App's values replace them in `deployment/kind/terraform/platform-secrets.tf`.

### Docker Compose (Local Development)

A Docker Compose configuration in `deployment/docker-compose/` provides a complete local development environment including the org, system and app servers (`compose.kinotic-servers.yml`), Elasticsearch, and supporting services. This is the recommended way to run Kinotic OS during development.

### KinD (Kubernetes in Docker)

For testing Kubernetes deployments locally, `deployment/kind/` provides a KinD setup with Terraform configurations that deploy the full stack into a local Kubernetes cluster. It has no Azure storage accounts, so the system server runs with `kinotic.systemApi.disableAzureStorage` set and a published UI is marked ready without being uploaded or served. `deployment/kind/README.md` describes where a deployment's workloads reach the servers.

### Development Server

A single-host development environment on Proxmox — a container per service, the three servers
behind an edge that routes the one forwarded port by SNI, three Elasticsearch nodes on their own
disks, and Cloud Hypervisor nodes on machines of their own — that keeps Front Door, the sites
account, and email in Azure and is designed to migrate its organizations to Kinotic Cloud. See
[Development Server](/platform/development-server).

### Cloud Providers (Terraform)

Terraform configurations in `deployment/terraform/` support deployment to:

- **AWS** — EC2-based deployment with configurable instance types
- **Azure** — VM-based deployment on Azure infrastructure

#### UI sites on Azure

`frontdoor.tf` calls `deployment/terraform/azure/modules/sites`, which creates everything a
published UI needs, once per environment, so nothing on Front Door, in DNS or in storage is
created when a UI is published: the sites storage account `st<prefix>sites` every site's
files live in under `sites/<hostname>/`, the Front Door Standard profile and endpoint every
site is served through, a Let's Encrypt wildcard certificate for `*.apps.<zone>` issued into
the cluster key vault by a DNS challenge and renewed by an apply within 30 days of expiry,
the wildcard custom domain and DNS record, an origin group that reads the account as the
profile's managed identity, and the route whose rules serve `sites/<hostname>/` for a
request's host. The module grants that identity Storage Blob Data Reader on the account and
the servers' identity Storage Blob Data Contributor, with which the system server signs the
URLs the publish and removal workloads act through, and `kinotic.tf` passes the sites domain
and the account's blob endpoint to the system server as `KINOTIC_SYSTEMAPI_UIDEPLOYMENT_*`
environment variables
(see [Configuration](/platform/configuration#ui-sites)). The certificate is issued by the
principal terraform runs as, which needs `lets_encrypt_email` set and holds Key Vault
Certificates Officer on the vault; the profile's identity is unknown until the profile
exists, so a first apply targets `module.sites.azurerm_cdn_frontdoor_profile.sites` before
applying the rest. The system server's development profile disables the provisioner instead.

A developer machine gets the same module from `deployment/terraform/azure/dev`, under
`apps-<environment>.<zone>` with a key vault of its own. The
[contributing guide](/platform/contributing#publishing-uis-against-azure) walks through it.
