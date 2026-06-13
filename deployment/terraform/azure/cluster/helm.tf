# ── Providers — connect via kubelogin ─────────────────────────────────────────

provider "helm" {
  kubernetes = {
    host                   = data.azurerm_kubernetes_cluster.main.kube_config.0.host
    cluster_ca_certificate = base64decode(data.azurerm_kubernetes_cluster.main.kube_config.0.cluster_ca_certificate)
    exec = {
      api_version = "client.authentication.k8s.io/v1beta1"
      command     = "kubelogin"
      args        = ["get-token", "--login", "azurecli", "--server-id", "6dae42f8-4368-4678-94ff-3960e28e3630"]
    }
  }
}

provider "kubernetes" {
  host                   = data.azurerm_kubernetes_cluster.main.kube_config.0.host
  cluster_ca_certificate = base64decode(data.azurerm_kubernetes_cluster.main.kube_config.0.cluster_ca_certificate)
  exec {
    api_version = "client.authentication.k8s.io/v1beta1"
    command     = "kubelogin"
    args        = ["get-token", "--login", "azurecli", "--server-id", "6dae42f8-4368-4678-94ff-3960e28e3630"]
  }
}

data "azurerm_kubernetes_cluster" "main" {
  name                = module.aks.cluster_name
  resource_group_name = azurerm_resource_group.main.name
  depends_on          = [module.aks]
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

# ── StorageClass: Premium ZRS ─────────────────────────────────────────────────

resource "kubernetes_storage_class_v1" "es_premium_zrs" {
  metadata {
    name = "es-premium-zrs"
    annotations = {
      "storageclass.kubernetes.io/is-default-class" = "false"
    }
  }
  storage_provisioner    = "disk.csi.azure.com"
  reclaim_policy         = "Retain"
  volume_binding_mode    = "WaitForFirstConsumer"
  allow_volume_expansion = true
  parameters = {
    skuName     = "Premium_ZRS"
    cachingmode = "None"
    fsType      = "ext4"
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

  set = [
    { name = "installCRDs", value = "true" },
  ]

  wait          = true
  wait_for_jobs = true
  timeout       = 300

  depends_on = [
    kubernetes_namespace.elastic_system,
    kubernetes_storage_class_v1.es_premium_zrs,
    module.identity,
  ]
}

# ── Elasticsearch ─────────────────────────────────────────────────────────────

resource "helm_release" "eck_stack" {
  name       = "elastic-stack"
  repository = "https://helm.elastic.co"
  chart      = "eck-stack"
  version    = "0.17.0"
  namespace  = kubernetes_namespace.elastic.metadata[0].name

  values = [
    file("${path.module}/../../../helm/eck-stack/values.yaml"),
    file("${path.module}/../../../helm/eck-stack/${var.beta_mode ? "values-azure-beta" : "values-azure"}.yaml"),
  ]

  wait    = true
  timeout = 600

  depends_on = [
    helm_release.eck_operator,
    kubernetes_namespace.elastic,
  ]
}

# ── NetworkPolicy on ES ───────────────────────────────────────────────────────

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

    ingress {
      from {
        pod_selector {}
      }
    }

    ingress {
      from {
        namespace_selector {
          match_labels = { "kubernetes.io/metadata.name" = "kinotic" }
        }
      }
    }

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
