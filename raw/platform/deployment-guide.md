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

- **Kinotic Server** — The core platform with configurable replicas, resource limits, and ingress
- **Elasticsearch** — Search and persistence cluster
- **Load Generator** — For performance testing

### Docker Compose (Local Development)

A Docker Compose configuration in `deployment/docker-compose/` provides a complete local development environment including the Kinotic server, Elasticsearch, and supporting services. This is the recommended way to run Kinotic OS during development.

### KinD (Kubernetes in Docker)

For testing Kubernetes deployments locally, `deployment/kind/` provides a KinD setup with Terraform configurations that deploy the full stack into a local Kubernetes cluster.

### Cloud Providers (Terraform)

Terraform configurations in `deployment/terraform/` support deployment to:

- **AWS** — EC2-based deployment with configurable instance types
- **Azure** — VM-based deployment on Azure infrastructure

#### Organization storage on Azure

`deployment/terraform/azure/cluster/org-storage.tf` creates what every organization's storage
account shares: the `rg-<prefix>-org-storage` resource group the accounts are created in, a
`private-endpoints` subnet in the platform VNet (`private_endpoint_subnet_cidr` in
`terraform.tfvars`), and the `privatelink.blob.core.windows.net` private DNS zone linked to
that VNet. The accounts themselves are created at runtime by kinotic-server, one per
organization, the first time a deployment publishes a UI. `keyvault.tf` grants the
kinotic-server identity the roles that takes on the resource group (Storage Account
Contributor, Storage Blob Data Contributor, Network Contributor), on the subnet (Network
Contributor, to join it) and on the DNS zone (Private DNS Zone Contributor), and
`kinotic.tf` hands the server the resource ids as
`KINOTIC_SYSTEMAPI_ORGANIZATIONSTORAGE_*` environment variables (see
[Configuration](/platform/configuration#organization-storage)).

#### UI sites on Azure

`frontdoor.tf` creates the one Front Door Standard profile and endpoint every published UI is
served through, under `apps.<zone>`. kinotic-server creates the rest at runtime: per
organization an origin group, an origin on the organization's storage account and a rule
set, and per site a custom domain with a managed certificate, a route, and the CNAME and
validation TXT records in the platform's DNS zone. `keyvault.tf` grants the kinotic-server
identity CDN Profile Contributor on the profile and DNS Zone Contributor on the zone, and
`kinotic.tf` passes the profile, endpoint and zone as `KINOTIC_SYSTEMAPI_UIDEPLOYMENT_*`
environment variables (see [Configuration](/platform/configuration#ui-sites)). The
`kinotic-server` values file for development disables the provisioner instead.
