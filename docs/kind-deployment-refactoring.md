# KinD Deployment Refactoring Plan

## Overview

The `deployment/kind/` directory provides local Kubernetes development infrastructure for Kinotic (formerly Structures). It currently consists of ~2,500 lines of Bash across 8 shell scripts, a KinD config, and Helm values files for 8 services. While functional, the scripts have accumulated complexity, anti-patterns, and unnecessary manual steps that make onboarding and maintenance harder than necessary.

This document proposes a refactoring strategy that:
1. **Eliminates all NodePort mappings** in favor of ingress-only access and `kubectl port-forward`
2. **Removes the local image build/load cycle** — pull published images from Docker Hub like any other Kubernetes environment
3. **Replaces imperative Bash orchestration** with Terraform, providing a unified tool for local KinD and cloud (AKS) deployments
4. **Addresses identified anti-patterns** with concrete alternatives

---

## Current Architecture

```
Host Machine
│
├── :80/:443 ──────────► NGINX Ingress ──► kinotic-server (UI, API, GraphQL, WebSocket)
│
├── :8080  (NodePort) ──► kinotic-server OpenAPI
├── :9090  (NodePort) ──► kinotic-server Web UI
├── :4000  (NodePort) ──► kinotic-server GraphQL
├── :58503 (NodePort) ──► Continuum STOMP
├── :58504 (NodePort) ──► Continuum REST
├── :9200  (NodePort) ──► Elasticsearch
├── :5555  (NodePort) ──► PostgreSQL
└── :8888  (NodePort) ──► Keycloak

Build/Load Cycle (current):
  ./gradlew :structures-server:bootBuildImage    ← builds image locally
  kind load docker-image kinoticai/...           ← manually pushes into KinD nodes
  imagePullPolicy: Never                         ← KinD never pulls, only uses preloaded
```

**8 NodePort mappings** exist alongside an ingress that already routes all application traffic. The image build/load cycle couples local Gradle builds to cluster provisioning, preventing Terraform from managing the full lifecycle.

---

## Anti-Patterns Identified

### 1. Manual Image Build and Preload into KinD

**Problem:** The current deploy flow requires building the kinotic-server image locally with `./gradlew :structures-server:bootBuildImage`, then manually loading it into KinD nodes with `kind load docker-image`. The Helm values set `imagePullPolicy: Never` to force use of the preloaded image.

This creates several issues:
- **Couples provisioning to a local Gradle/Java build environment** — every developer needs Java 21+, Gradle, and the full source tree just to stand up the cluster
- **Prevents Terraform from managing the full lifecycle** — Terraform can create clusters and deploy Helm charts, but it cannot run Gradle builds or `kind load`, forcing a shell wrapper to remain
- **Diverges from how every other environment works** — staging, production, and cloud deployments all pull images from a registry. The local dev path is a special snowflake
- **`imagePullPolicy: Never` is fragile** — if a developer forgets to preload or the image name drifts, pods silently fail with `ErrImageNeverPull`
- **`lib/images.sh` (240 lines)** exists solely to manage this build/load cycle

**Alternative:** Publish kinotic-server images to Docker Hub as part of CI/CD (which likely already happens). Set `imagePullPolicy: IfNotPresent` in Helm values. KinD clusters can pull from public registries natively — no preloading needed. The cluster provisioning becomes purely declarative: create cluster, deploy charts, Kubernetes pulls images.

For developers testing unpublished local changes, `kind load docker-image` remains available as an **optional manual step**, not a required part of provisioning.

### 2. NodePort Sprawl

**Problem:** 8 services exposed via NodePort + extraPortMappings when the ingress already handles application routing. Each NodePort requires:
- An entry in `kind-config.yaml` (extraPortMappings)
- A matching NodePort value in the service's Helm values
- Documentation of the port mapping

This is the single largest source of configuration complexity and host-port conflicts.

**Alternative:** Remove all NodePorts. Route all application traffic through the existing NGINX ingress (ports 80/443). For infrastructure services that genuinely need direct access (Elasticsearch, PostgreSQL, Keycloak admin), use `kubectl port-forward` on demand.

### 3. Imperative Bash Orchestration (~2,500 lines)

**Problem:** The deployment flow is a chain of imperative `helm upgrade --install`, `kubectl apply`, `kubectl patch`, and polling loops spread across `deploy.sh`, `cluster.sh`, `config.sh`, etc. This makes it:
- **Hard to reason about state** — you can't tell what's deployed without running `status`
- **Not idempotent** — re-running `deploy` deletes and recreates secrets, ConfigMaps, etc.
- **Fragile** — ordering dependencies are implicit in function call order, not declared
- **A barrier to unification** — can't share deployment logic between local KinD and cloud AKS

**Alternative:** Replace with Terraform (see [Tool Recommendation](#tool-recommendation) below).

### 4. Hardcoded Image Tags

**Problem:** The kinotic-server image tag `3.5.2` is hardcoded in `config/structures-server/values.yaml` (lines 36, 49) separately from `gradle.properties`. The deploy script reads the version from `gradle.properties` at runtime, but the values file has a stale literal. This creates drift.

**Alternative:** Use a Terraform variable for the image tag, defaulting to `latest` or read from `gradle.properties`. The tag is set once and flows through to all Helm releases that need it.

### 5. Hardcoded Credentials in Version Control

**Problem:** Credentials appear as literals in checked-in YAML files:
- `keycloak/values.yaml`: `admin/admin`, `keycloak/keycloak`
- `postgresql/values.yaml`: `keycloak/keycloak`

**Alternative:** Even for local dev, use Terraform variables with defaults. This prevents accidental copy-paste into non-dev environments and establishes good habits. Terraform's `sensitive = true` flag prevents credentials from appearing in plan output.

### 6. Fragmented Configuration (Duplicate Sources of Truth)

**Problem:** The same values are defined in multiple places:
- Elasticsearch version in `deployment/kind/config/elasticsearch/values.yaml` AND `deployment/helm/elasticsearch/values.yaml`
- Image tags in kind values AND helm chart values
- OIDC config duplicated across kind and helm directories
- Service hostnames hardcoded in multiple scripts and values files

**Alternative:** Use a single set of Helm chart values as the base. The `deployment/kind/config/` files should contain **only KinD-specific overrides**, layered on top of the base chart values via Terraform's `helm_release` values list. Never duplicate a value that the base chart already defines.

### 7. No Helm Chart Version Pinning

**Problem:** Helm chart dependencies (bitnami/postgresql, elastic/eck-operator, ingress-nginx, jetstack/cert-manager) are installed without version constraints. A chart update can silently break the setup.

**Alternative:** Pin every chart version in Terraform `helm_release` resources. Terraform state makes the deployed versions explicit and auditable.

### 8. Complex Bash Parsing and Template Substitution

**Problem:**
- CoreDNS configuration uses `sed` template substitution on a YAML file with `${INGRESS_IP}` and `${HOSTNAME}` placeholders — error-prone and hard to validate
- Pod status parsing relies on `awk` field splitting of `kubectl` output — fragile if output format changes
- TLS certificate file matching uses `ls localhost+*.pem | grep -v '\-key' | head -1` — assumes mkcert naming conventions

**Alternative:** Terraform's `templatefile()` function handles variable substitution cleanly. The `kubectl` provider can apply manifests directly. TLS can be managed through cert-manager resources declared in Terraform.

### 9. Lack of Idempotency

**Problem:** Several operations delete-then-create instead of apply-or-update:
- TLS secrets: `kubectl delete secret ... || true` then `kubectl create secret`
- Keycloak realm ConfigMap: always deletes and recreates
- CoreDNS config: generates temp file, replaces ConfigMap unconditionally

**Alternative:** Terraform is inherently idempotent — it computes a diff and applies only what changed. `terraform apply` run twice with no changes produces no mutations.

### 10. Dev-Only Security Relaxations Without Guardrails

**Problem:** The NGINX ingress config sets `annotations-risk-level: Critical` and `allow-snippet-annotations: true`. These are necessary for local dev but dangerous if the config is used as a template for production.

**Alternative:** Keep these values in a KinD-specific values file that is clearly separated from production configs. Terraform's environment-based variable files (`terraform.tfvars` vs `prod.tfvars`) make this separation explicit.

### 11. Monolithic Deploy Script

**Problem:** `deploy.sh` is 1,135 lines handling 10+ services in a single sequential flow. Adding a new service means understanding the entire file.

**Alternative:** Each service becomes an independent Terraform `helm_release` resource. Dependencies are declared via `depends_on`, not implied by function call order. Adding a new service is adding a new resource block.

---

## Tool Recommendation: Terraform

### Why Terraform is the Right Choice

With the image build/load cycle removed from provisioning, Terraform can manage the **complete lifecycle** — from cluster creation to fully deployed services — with no shell wrapper needed for the default path.

**Unified local + cloud:** Terraform already manages cloud infrastructure (AKS, networking, DNS). Using it for KinD means the same tool, same language (HCL), and similar patterns manage both environments. Developers learn one deployment approach, not two.

**Full lifecycle in one tool:**
```
terraform apply
  → Creates KinD cluster (kind provider)
  → Configures Helm provider (pointed at new cluster)
  → Deploys ingress-nginx, cert-manager, ECK, Elasticsearch
  → Deploys kinotic-server (Kubernetes pulls image from Docker Hub)
  → Optionally deploys PostgreSQL + Keycloak
  → Done. No shell scripts.
```

**Key advantages over Helmfile for this project:**
- Helmfile still requires `kind` CLI for cluster creation and a shell wrapper — it only orchestrates Helm
- Terraform manages cluster + deployments as a single state, so `terraform destroy` cleans up everything
- The team will use Terraform for AKS anyway (see `docs/azure-aks-elasticsearch.md`) — one tool to learn, not two
- `terraform plan` shows a complete preview of every change across cluster and services
- State tracking means you always know exactly what's deployed

### Example Terraform Configuration

#### `main.tf` — Providers and Cluster

```hcl
terraform {
  required_version = ">= 1.5"

  required_providers {
    kind = {
      source  = "tehcyx/kind"
      version = "~> 0.7"
    }
    helm = {
      source  = "hashicorp/helm"
      version = "~> 2.17"
    }
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "~> 2.35"
    }
  }
}

# ── KinD Cluster ──────────────────────────────────────────

resource "kind_cluster" "kinotic" {
  name           = var.cluster_name
  wait_for_ready = true

  kind_config {
    kind        = "Cluster"
    api_version = "kind.x-k8s.io/v1alpha4"

    node {
      role = "control-plane"

      extra_port_mappings {
        container_port = 80
        host_port      = 80
      }
      extra_port_mappings {
        container_port = 443
        host_port      = 443
      }
    }

    node { role = "worker" }
    node { role = "worker" }
    node { role = "worker" }
  }
}

# ── Providers configured from cluster output ──────────────

provider "helm" {
  kubernetes {
    host                   = kind_cluster.kinotic.endpoint
    cluster_ca_certificate = kind_cluster.kinotic.cluster_ca_certificate
    client_certificate     = kind_cluster.kinotic.client_certificate
    client_key             = kind_cluster.kinotic.client_key
  }
}

provider "kubernetes" {
  host                   = kind_cluster.kinotic.endpoint
  cluster_ca_certificate = kind_cluster.kinotic.cluster_ca_certificate
  client_certificate     = kind_cluster.kinotic.client_certificate
  client_key             = kind_cluster.kinotic.client_key
}
```

#### `ingress.tf` — NGINX Ingress + cert-manager

```hcl
resource "helm_release" "ingress_nginx" {
  name             = "ingress-nginx"
  namespace        = "ingress-nginx"
  create_namespace = true
  repository       = "https://kubernetes.github.io/ingress-nginx"
  chart            = "ingress-nginx"
  version          = "4.12.0"
  wait             = true
  timeout          = 300

  values = [file("${path.module}/config/ingress-nginx/values.yaml")]

  depends_on = [kind_cluster.kinotic]
}

resource "helm_release" "cert_manager" {
  name             = "cert-manager"
  namespace        = "cert-manager"
  create_namespace = true
  repository       = "https://charts.jetstack.io"
  chart            = "cert-manager"
  version          = "v1.16.3"
  wait             = true

  values = [file("${path.module}/config/cert-manager/values.yaml")]

  depends_on = [kind_cluster.kinotic]
}
```

#### `elasticsearch.tf` — ECK Operator + Elasticsearch

```hcl
resource "helm_release" "eck_operator" {
  name             = "eck-operator"
  namespace        = "elastic-system"
  create_namespace = true
  repository       = "https://helm.elastic.co"
  chart            = "eck-operator"
  version          = "2.16.1"
  wait             = true

  values = [file("${path.module}/config/eck-operator/values.yaml")]

  depends_on = [kind_cluster.kinotic]
}

resource "helm_release" "elasticsearch" {
  name      = "elasticsearch"
  namespace = "default"
  chart     = "${path.module}/../helm/elasticsearch"
  wait      = true
  timeout   = 600

  values = [
    file("${path.module}/../helm/elasticsearch/values.yaml"),
    file("${path.module}/config/elasticsearch/values.yaml"),  # KinD overrides only
  ]

  depends_on = [helm_release.eck_operator]
}
```

#### `kinotic.tf` — Kinotic Server

```hcl
resource "helm_release" "kinotic_server" {
  name      = "kinotic-server"
  namespace = "default"
  chart     = "${path.module}/../helm/structures"
  wait      = true
  timeout   = 600

  values = [file("${path.module}/config/structures-server/values.yaml")]

  # Image tag from variable — no hardcoding in values.yaml
  set {
    name  = "image.tag"
    value = var.kinotic_version
  }

  # Pull from Docker Hub — no local build/load needed
  set {
    name  = "image.pullPolicy"
    value = "IfNotPresent"
  }

  depends_on = [
    helm_release.ingress_nginx,
    helm_release.elasticsearch,
  ]
}
```

#### `keycloak.tf` — Optional Keycloak + PostgreSQL

```hcl
resource "helm_release" "postgresql" {
  count = var.enable_keycloak ? 1 : 0

  name       = "keycloak-db"
  namespace  = "default"
  repository = "https://charts.bitnami.com/bitnami"
  chart      = "postgresql"
  version    = "16.4.1"
  wait       = true

  values = [file("${path.module}/config/postgresql/values.yaml")]

  set_sensitive {
    name  = "auth.username"
    value = var.keycloak_db_username
  }
  set_sensitive {
    name  = "auth.password"
    value = var.keycloak_db_password
  }

  depends_on = [kind_cluster.kinotic]
}

resource "helm_release" "keycloak" {
  count = var.enable_keycloak ? 1 : 0

  name      = "keycloak"
  namespace = "default"
  chart     = "${path.module}/charts/keycloak"
  wait      = true
  timeout   = 600

  values = [file("${path.module}/config/keycloak/values.yaml")]

  set_sensitive {
    name  = "auth.adminPassword"
    value = var.keycloak_admin_password
  }

  depends_on = [helm_release.postgresql]
}
```

#### `variables.tf`

```hcl
variable "cluster_name" {
  description = "KinD cluster name"
  type        = string
  default     = "kinotic-cluster"
}

variable "kinotic_version" {
  description = "Kinotic server image tag (published to Docker Hub)"
  type        = string
  default     = "latest"
}

variable "enable_keycloak" {
  description = "Deploy Keycloak + PostgreSQL for OIDC"
  type        = bool
  default     = false
}

variable "keycloak_db_username" {
  description = "PostgreSQL username for Keycloak"
  type        = string
  default     = "keycloak"
  sensitive   = true
}

variable "keycloak_db_password" {
  description = "PostgreSQL password for Keycloak"
  type        = string
  default     = "keycloak"
  sensitive   = true
}

variable "keycloak_admin_password" {
  description = "Keycloak admin console password"
  type        = string
  default     = "admin"
  sensitive   = true
}

variable "worker_count" {
  description = "Number of KinD worker nodes"
  type        = number
  default     = 3
}
```

#### `outputs.tf`

```hcl
output "cluster_name" {
  value = kind_cluster.kinotic.name
}

output "kubeconfig" {
  value     = kind_cluster.kinotic.kubeconfig
  sensitive = true
}

output "app_url" {
  value = "https://localhost"
}

output "port_forward_commands" {
  value = {
    elasticsearch = "kubectl port-forward svc/structures-es-es-http 9200:9200"
    postgresql    = var.enable_keycloak ? "kubectl port-forward svc/keycloak-db-postgresql 5432:5432" : null
    keycloak      = var.enable_keycloak ? "kubectl port-forward svc/keycloak 8888:8888" : null
  }
}
```

#### `terraform.tfvars` (default local dev values, checked in)

```hcl
cluster_name    = "kinotic-cluster"
kinotic_version = "latest"
enable_keycloak = false
worker_count    = 3
```

### Usage

```bash
# One-command standup — no Java, no Gradle, no kind CLI needed
cd deployment/kind
terraform init
terraform apply

# With Keycloak
terraform apply -var="enable_keycloak=true"

# Specific version
terraform apply -var="kinotic_version=3.5.2"

# Preview changes
terraform plan

# Tear down everything (cluster + all services)
terraform destroy

# Port-forward for direct infra access (when needed)
kubectl port-forward svc/structures-es-es-http 9200:9200
```

### Developing with Unpublished Local Changes

For the less-common case where a developer needs to test local code changes against the KinD cluster:

```bash
# 1. Build the image locally (requires Java 21 + Gradle)
./gradlew :structures-server:bootBuildImage

# 2. Load into the running KinD cluster
kind load docker-image kinoticai/kinotic-server:3.5.2 --name kinotic-cluster

# 3. Restart the deployment to pick up the new image
kubectl rollout restart deployment/kinotic-server
```

This is an **opt-in developer workflow**, not part of provisioning. Most developers will use published Docker Hub images and never run these commands.

---

## Why Not Helmfile or Ansible?

### Helmfile

Helmfile orchestrates Helm releases well, but:
- **Cannot create the KinD cluster** — still needs `kind` CLI and a shell wrapper
- **Cannot manage cloud resources** — a separate Terraform config is needed for AKS anyway
- **Two tools instead of one** — developers learn Helmfile for local, Terraform for cloud
- **No state tracking** — Helmfile reads Helm state but doesn't track cluster-level resources

Helmfile would be the right choice if the project only needed local KinD and had no cloud deployment story. Since Kinotic targets both local and AKS, Terraform provides better unification.

### Ansible

- Overkill for local-only KinD — Ansible excels at configuring remote hosts
- Requires Python, ansible-core, and collections
- Slower execution than Terraform for Helm-only workflows
- Not recommended for this use case

---

## Proposed Architecture (Post-Refactoring)

### Image Flow: Docker Hub Pull

```
CI/CD Pipeline                    Developer Machine
┌──────────────┐                 ┌──────────────────────────────┐
│ Build        │                 │                              │
│ Test         │                 │  terraform apply             │
│ Publish to   │──── images ───►│    → kind_cluster created    │
│ Docker Hub   │                 │    → helm releases deployed  │
└──────────────┘                 │    → K8s pulls from Hub      │
                                 │    → Done.                   │
                                 │                              │
                                 │  (optional, for local dev):  │
                                 │  ./gradlew bootBuildImage    │
                                 │  kind load docker-image ...  │
                                 └──────────────────────────────┘
```

### Access Model: Ingress-Only + Port-Forward

```
Host Machine
│
├── :80/:443 ──► NGINX Ingress ──► kinotic-server (all app traffic)
│
│   On-demand (when needed for debugging):
│   kubectl port-forward svc/structures-es-es-http 9200:9200
│   kubectl port-forward svc/keycloak 8888:8888
│   kubectl port-forward svc/keycloak-db-postgresql 5432:5432
```

**Changes:**
- Remove ALL NodePort extraPortMappings from `kind-config.yaml` (lines 28-66)
- Remove ALL NodePort service types from Helm values
- Keep only ports 80 and 443 for ingress
- Add optional ingress routes for Elasticsearch and Keycloak (e.g., `es.localhost`, `auth.localhost`) OR document `kubectl port-forward` commands

**Benefits:**
- `kind-config.yaml` drops from 88 lines to ~30
- No more host port conflicts (only 80/443 needed)
- No more NodePort↔extraPortMapping↔values.yaml synchronization
- Cleaner separation: ingress handles routing, not port mapping

### File Structure (with Terraform)

```
deployment/kind/
├── main.tf                          # Providers, KinD cluster
├── ingress.tf                       # ingress-nginx + cert-manager
├── elasticsearch.tf                 # ECK operator + Elasticsearch
├── kinotic.tf                       # Kinotic server
├── keycloak.tf                      # Optional PostgreSQL + Keycloak
├── variables.tf                     # All input variables
├── outputs.tf                       # Cluster info, access URLs
├── terraform.tfvars                 # Default local dev values
├── config/
│   ├── cert-manager/values.yaml
│   ├── eck-operator/values.yaml
│   ├── elasticsearch/values.yaml    # KinD overrides only (no NodePort)
│   ├── ingress-nginx/values.yaml
│   ├── keycloak/values.yaml         # No NodePort, no hardcoded creds
│   ├── postgresql/values.yaml       # No NodePort, no hardcoded creds
│   └── structures-server/
│       └── values.yaml              # No hardcoded image tag, pullPolicy: IfNotPresent
├── charts/keycloak/                 # Local Keycloak chart (unchanged)
└── node-update.sh                   # Standalone node disruption testing tool
```

**What's removed:**
- `kind-cluster.sh` (main CLI, ~1,200 lines) → `terraform apply` / `terraform destroy`
- `lib/deploy.sh` (1,135 lines) → Terraform `helm_release` resources
- `lib/config.sh` (317 lines) → `variables.tf` + `terraform.tfvars`
- `lib/prerequisites.sh` (327 lines) → Terraform checks its own providers
- `lib/logging.sh` (150 lines) → Terraform has built-in progress output
- `lib/cluster.sh` (320 lines) → `kind_cluster` resource
- `lib/images.sh` (240 lines) → **deleted entirely** (images pulled from Docker Hub)
- `config/kind-config.yaml` → inline in `kind_cluster` resource (or kept as reference)
- `lib/node_update.sh` (210 lines) → **kept as standalone `node-update.sh`** (special-purpose testing tool)

---

## NodePort Removal: Detailed Changes

### `kind-config.yaml` (or inline in Terraform)

**Remove** lines 28-66 (all NodePort extraPortMappings). Keep only ingress ports:

```yaml
nodes:
  - role: control-plane
    extraPortMappings:
      - containerPort: 80
        hostPort: 80
        protocol: TCP
      - containerPort: 443
        hostPort: 443
        protocol: TCP
  - role: worker
  - role: worker
  - role: worker
```

### `config/elasticsearch/values.yaml`

Remove the NodePort service configuration. Elasticsearch is accessed via `kubectl port-forward` or through an ingress route if needed.

### `config/postgresql/values.yaml`

Remove `type: NodePort` and `nodePort: 30555`. Use `type: ClusterIP` (the default). Access via `kubectl port-forward svc/keycloak-db-postgresql 5432:5432`.

### `config/keycloak/values.yaml`

Remove `type: NodePort` and `nodePort: 30888`. Optionally add a Keycloak ingress route (e.g., `auth.localhost/`) or use `kubectl port-forward svc/keycloak 8888:8888`.

### `config/structures-server/values.yaml`

- Remove any NodePort references
- Change `imagePullPolicy: Never` → `imagePullPolicy: IfNotPresent`
- Remove hardcoded image tag (set via Terraform variable)
- All traffic flows through the NGINX ingress

---

## Migration Path

### Phase 1: Remove NodePorts (Low Risk, No New Tools)

1. Strip all NodePort extraPortMappings from `kind-config.yaml` except 80/443
2. Change all service types from `NodePort` to `ClusterIP` in values files
3. Add `port-forward` subcommand to existing `kind-cluster.sh` as a stopgap
4. Update README to document port-forward access
5. Test that ingress-based access still works for all application routes

This can be done independently of any tooling changes.

### Phase 2: Switch Image Strategy

1. Ensure kinotic-server images are published to Docker Hub via CI/CD
2. Change `imagePullPolicy` from `Never` to `IfNotPresent` in values files
3. Remove the automatic `bootBuildImage` + `kind load` from the deploy flow
4. Remove `lib/images.sh`
5. Document the optional local-build workflow for developers

### Phase 3: Introduce Terraform

1. Install Terraform: `brew install terraform`
2. Create the `.tf` files as described above
3. Run `terraform init` + `terraform plan` to validate
4. Verify `terraform apply` creates a working cluster from scratch
5. Test `terraform destroy` cleanly removes everything

### Phase 4: Remove Shell Scripts

1. Delete `kind-cluster.sh` and the entire `lib/` directory
2. Keep `node-update.sh` as a standalone script
3. Update README to document the Terraform workflow
4. Remove `config/kind-config.yaml` (cluster config is now inline in Terraform)

### Phase 5: Clean Up Configuration

1. Remove hardcoded image tags from all values files
2. Remove hardcoded credentials from values files (use Terraform variables)
3. Eliminate duplicate values between `deployment/kind/config/` and `deployment/helm/`
4. Ensure KinD config files contain only KinD-specific overrides

---

## Unification with Cloud Deployments

With Terraform managing the local KinD cluster, the path to AKS (or any cloud) becomes a matter of swapping the cluster resource and adjusting variables:

```
deployment/
├── kind/                        # Local development
│   ├── main.tf                  # kind_cluster + helm_releases
│   ├── variables.tf
│   └── config/                  # KinD-specific Helm overrides
│
├── aks/                         # Azure cloud (future)
│   ├── main.tf                  # azurerm_kubernetes_cluster + helm_releases
│   ├── variables.tf
│   └── config/                  # AKS-specific Helm overrides
│
├── helm/                        # Shared Helm charts (unchanged)
│   ├── structures/
│   └── elasticsearch/
│
└── modules/                     # Shared Terraform modules (optional)
    └── kinotic-stack/           # Common helm_release definitions
        ├── main.tf              #   reused by both kind/ and aks/
        └── variables.tf
```

The `helm_release` resources for kinotic-server, Elasticsearch, ingress, etc. can be extracted into a shared Terraform module. Each environment (kind, aks) provides its own cluster resource and environment-specific variable values, but the deployment logic is shared.

---

## Summary

| What | Before | After |
|------|--------|-------|
| Provisioning tool | ~2,500 lines of Bash | Terraform (~200 lines of HCL) |
| Image strategy | Local build → `kind load` → `pullPolicy: Never` | Docker Hub pull → `pullPolicy: IfNotPresent` |
| Prerequisites to stand up cluster | Java 21, Gradle, Docker, kind, kubectl, helm | Docker, Terraform |
| Stand up command | `kind-cluster.sh create && kind-cluster.sh deploy` | `terraform apply` |
| Tear down command | `kind-cluster.sh delete` | `terraform destroy` |
| NodePorts | 8 services, 8 port mappings | 0 (ingress + port-forward) |
| Host ports consumed | 10 (80, 443, 8080, 9090, 4000, 58503, 58504, 9200, 5555, 8888) | 2 (80, 443) |
| Chart version pinning | None | All pinned in `helm_release` resources |
| Image tag source of truth | Duplicated (gradle.properties + values.yaml) | Single (Terraform variable) |
| Idempotency | Partial (delete-then-create) | Full (`terraform apply`) |
| Cloud deployment alignment | Completely separate tooling | Same tool, sharable modules |
| Local dev with unpublished code | Required (build is part of deploy) | Optional (`kind load` when needed) |
