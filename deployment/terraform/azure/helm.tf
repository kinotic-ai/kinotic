# ── Providers — connect using AKS cluster credentials ─────────────────────────
provider "helm" {
  kubernetes {
    host                   = data.azurerm_kubernetes_cluster.main.kube_config.0.host
    client_certificate     = base64decode(data.azurerm_kubernetes_cluster.main.kube_config.0.client_certificate)
    client_key             = base64decode(data.azurerm_kubernetes_cluster.main.kube_config.0.client_key)
    cluster_ca_certificate = base64decode(data.azurerm_kubernetes_cluster.main.kube_config.0.cluster_ca_certificate)
  }
}

provider "kubernetes" {
  host                   = data.azurerm_kubernetes_cluster.main.kube_config.0.host
  client_certificate     = base64decode(data.azurerm_kubernetes_cluster.main.kube_config.0.client_certificate)
  client_key             = base64decode(data.azurerm_kubernetes_cluster.main.kube_config.0.client_key)
  cluster_ca_certificate = base64decode(data.azurerm_kubernetes_cluster.main.kube_config.0.cluster_ca_certificate)
}

data "azurerm_kubernetes_cluster" "main" {
  name                = module.aks.cluster_name
  resource_group_name = azurerm_resource_group.main.name

  depends_on = [module.aks]
}

# ── Namespaces ────────────────────────────────────────────────────────────────
resource "kubernetes_namespace" "elastic_system" {
  metadata {
    name   = "elastic-system"
    labels = { "app.kubernetes.io/managed-by" = "terraform" }
  }
  depends_on = [module.aks]
}

resource "kubernetes_namespace" "elastic" {
  metadata {
    name   = "elastic"
    labels = { "app.kubernetes.io/managed-by" = "terraform" }
  }
  depends_on = [module.aks]
}

resource "kubernetes_namespace" "kinotic" {
  metadata {
    name   = "kinotic"
    labels = { "app.kubernetes.io/managed-by" = "terraform" }
  }
  depends_on = [module.aks]
}

# ── StorageClass: Premium ZRS for Elasticsearch ───────────────────────────────
resource "kubernetes_manifest" "sc_es_premium_zrs" {
  manifest = {
    apiVersion = "storage.k8s.io/v1"
    kind       = "StorageClass"
    metadata = {
      name = "es-premium-zrs"
      annotations = {
        "storageclass.kubernetes.io/is-default-class" = "false"
      }
    }
    provisioner           = "disk.csi.azure.com"
    reclaimPolicy         = "Retain"
    volumeBindingMode     = "WaitForFirstConsumer"
    allowVolumeExpansion  = true
    parameters = {
      skuName     = "Premium_ZRS"
      cachingmode = "None"
      fsType      = "ext4"
    }
  }
  depends_on = [module.aks]
}

# ── ECK Operator ──────────────────────────────────────────────────────────────
resource "helm_release" "eck_operator" {
  name       = "elastic-operator"
  repository = "https://helm.elastic.co"
  chart      = "eck-operator"
  version    = "3.3.1"
  namespace  = kubernetes_namespace.elastic_system.metadata[0].name

  values = [file("${path.module}/helm/eck-operator/values.yaml")]

  wait          = true
  wait_for_jobs = true
  timeout       = 300

  depends_on = [
    kubernetes_namespace.elastic_system,
    kubernetes_manifest.sc_es_premium_zrs,
    module.identity,
  ]
}

# ── Elasticsearch (via eck-stack chart) ───────────────────────────────────────
resource "helm_release" "eck_stack" {
  name       = "elastic-stack"
  repository = "https://helm.elastic.co"
  chart      = "eck-stack"
  version    = "0.17.0"
  namespace  = kubernetes_namespace.elastic.metadata[0].name

  values = [
    file("${path.module}/../../helm/eck-stack/values.yaml"),
    file("${path.module}/../../helm/eck-stack/${var.beta_mode ? "values-azure-beta" : "values-azure"}.yaml"),
  ]

  wait    = true
  timeout = 600

  depends_on = [
    helm_release.eck_operator,
    kubernetes_namespace.elastic,
  ]
}

# ── NetworkPolicy: restrict ES ingress ────────────────────────────────────────
# Allow traffic from within the elastic namespace (ES inter-node),
# from kinotic namespace (server, migration), and from elastic-system
# (ECK operator). Blocks everything else.

resource "kubernetes_network_policy" "elasticsearch" {
  metadata {
    name      = "elasticsearch-ingress"
    namespace = kubernetes_namespace.elastic.metadata[0].name
  }

  spec {
    pod_selector {
      match_labels = {
        "elasticsearch.k8s.elastic.co/cluster-name" = "kinotic-es"
      }
    }

    policy_types = ["Ingress"]

    # ES inter-node traffic (same namespace)
    ingress {
      from {
        pod_selector {}
      }
    }

    # kinotic namespace (server + migration)
    ingress {
      from {
        namespace_selector {
          match_labels = { "kubernetes.io/metadata.name" = "kinotic" }
        }
      }
    }

    # ECK operator
    ingress {
      from {
        namespace_selector {
          match_labels = { "kubernetes.io/metadata.name" = "elastic-system" }
        }
      }
    }
  }

  depends_on = [kubernetes_namespace.elastic]
}
