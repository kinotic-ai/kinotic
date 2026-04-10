# ── Namespaces ─────────────────────────────────────────────

resource "kubernetes_namespace" "elastic_system" {
  metadata {
    name   = "elastic-system"
    labels = { "app.kubernetes.io/managed-by" = "terraform" }
  }
  depends_on = [kind_cluster.kinotic]
}

resource "kubernetes_namespace" "elastic" {
  metadata {
    name   = "elastic"
    labels = { "app.kubernetes.io/managed-by" = "terraform" }
  }
  depends_on = [kind_cluster.kinotic]
}

# ── ECK Operator ──────────────────────────────────────────

resource "helm_release" "eck_operator" {
  name       = "elastic-operator"
  namespace  = kubernetes_namespace.elastic_system.metadata[0].name
  repository = "https://helm.elastic.co"
  chart      = "eck-operator"
  version    = "3.3.1"
  wait       = true
  timeout    = var.deploy_timeout

  values = [file("${path.module}/../config/eck-operator/values.yaml")]

  set = [
    { name = "installCRDs", value = "true" },
    { name = "managedNamespaces", value = "{elastic}" },
  ]

  depends_on = [kubernetes_namespace.elastic_system]
}

# ── Elasticsearch (via eck-stack chart) ───────────────────

resource "helm_release" "elasticsearch" {
  name       = "elastic-stack"
  namespace  = kubernetes_namespace.elastic.metadata[0].name
  repository = "https://helm.elastic.co"
  chart      = "eck-stack"
  version    = "0.17.0"
  wait       = true
  timeout    = var.deploy_timeout

  values = [
    file("${path.module}/../../helm/eck-stack/values.yaml"),
    file("${path.module}/../../helm/eck-stack/values-kind.yaml"),
  ]

  depends_on = [helm_release.eck_operator, kubernetes_namespace.elastic]
}

# ── ES Secret Sync ────────────────────────────────────────
# Copies the ECK elastic-user secret from elastic → kinotic namespace.
# Runs as a Helm pre-install hook Job with retry logic, waiting for
# ECK to finish creating the secret.

resource "helm_release" "es_secret_sync" {
  name      = "es-secret-sync"
  namespace = kubernetes_namespace.kinotic.metadata[0].name
  chart     = "${path.module}/../../helm/es-secret-sync"
  wait      = true
  timeout   = 300

  depends_on = [helm_release.elasticsearch, kubernetes_namespace.kinotic]
}

# ── NetworkPolicy: restrict ES ingress ────────────────────
# Allow traffic from kinotic namespace (server, migration) and
# elastic-system (ECK operator). Block everything else.

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
