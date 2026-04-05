# ── Terraform Configuration ────────────────────────────────
#
# KinD local development cluster for Kinotic.
# Creates a multi-node cluster and deploys all services via Helm.
#
# Usage:
#   terraform init
#   terraform apply                              # basic (Elasticsearch only)
#   terraform apply -var="enable_keycloak=true"  # with OIDC
#   terraform destroy                            # tear down everything

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
    local = {
      source  = "hashicorp/local"
      version = "~> 2.5"
    }
  }
}

# ── KinD Cluster ──────────────────────────────────────────

resource "kind_cluster" "kinotic" {
  name           = var.cluster_name
  node_image     = var.node_image
  wait_for_ready = true

  kind_config {
    kind        = "Cluster"
    api_version = "kind.x-k8s.io/v1alpha4"

    node {
      role = "control-plane"

      # Web UI / health check
      extra_port_mappings {
        container_port = 30443
        host_port      = 443
        protocol       = "TCP"
      }
      # OpenAPI
      extra_port_mappings {
        container_port = 30080
        host_port      = 8080
        protocol       = "TCP"
      }
      # GraphQL
      extra_port_mappings {
        container_port = 30400
        host_port      = 4000
        protocol       = "TCP"
      }
      # STOMP / WebSocket
      extra_port_mappings {
        container_port = 30503
        host_port      = 58503
        protocol       = "TCP"
      }
      # Keycloak (conditional, but port mapping is harmless if unused)
      extra_port_mappings {
        container_port = 30888
        host_port      = 8888
        protocol       = "TCP"
      }
    }

    dynamic "node" {
      for_each = range(var.worker_count)
      content {
        role = "worker"
      }
    }

    networking {
      api_server_port = 6443
      pod_subnet      = "10.244.0.0/16"
      service_subnet  = "10.96.0.0/12"
    }
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

