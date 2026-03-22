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

      extra_port_mappings {
        container_port = 80
        host_port      = 80
        protocol       = "TCP"
      }
      extra_port_mappings {
        container_port = 443
        host_port      = 443
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

# ── Control-plane node label for ingress scheduling ───────

resource "kubernetes_labels" "control_plane_ingress_ready" {
  api_version = "v1"
  kind        = "Node"

  metadata {
    name = "${var.cluster_name}-control-plane"
  }

  labels = {
    "ingress-ready" = "true"
  }

  depends_on = [kind_cluster.kinotic]
}
