# ── Observability: Loki + Alloy + Grafana ─────────────────

resource "kubernetes_namespace" "observability" {
  metadata {
    name   = "observability"
    labels = { "app.kubernetes.io/managed-by" = "terraform" }
  }
  depends_on = [kind_cluster.kinotic]
}

# ── Copy TLS secret to observability namespace ────────────

resource "kubernetes_secret" "observability_tls" {
  count = var.use_mkcert ? 1 : 0

  metadata {
    name      = "kinotic-tls"
    namespace = kubernetes_namespace.observability.metadata[0].name
  }

  type = "kubernetes.io/tls"

  data = {
    "tls.crt" = data.local_file.tls_cert[0].content
    "tls.key" = data.local_file.tls_key[0].content
  }

  depends_on = [terraform_data.mkcert, kubernetes_namespace.observability]
}

# ── Loki (log storage) ───────────────────────────────────

resource "helm_release" "loki" {
  name       = "loki"
  namespace  = kubernetes_namespace.observability.metadata[0].name
  repository = "https://grafana.github.io/helm-charts"
  chart      = "loki"
  version    = "6.29.0"
  wait       = true
  timeout    = var.deploy_timeout

  values = [file("${path.module}/../../helm/observability/values-loki.yaml")]

  depends_on = [kubernetes_namespace.observability]
}

# ── Alloy config (ConfigMap) ─────────────────────────────

resource "kubernetes_config_map" "alloy_config" {
  metadata {
    name      = "alloy-config"
    namespace = kubernetes_namespace.observability.metadata[0].name
  }

  data = {
    "config.alloy" = file("${path.module}/../../helm/observability/alloy-config.alloy")
  }

  depends_on = [kubernetes_namespace.observability]
}

# ── Alloy (log collector, DaemonSet) ─────────────────────

resource "helm_release" "alloy" {
  name       = "alloy"
  namespace  = kubernetes_namespace.observability.metadata[0].name
  repository = "https://grafana.github.io/helm-charts"
  chart      = "alloy"
  version    = "0.12.0"
  wait       = true
  timeout    = var.deploy_timeout

  values = [file("${path.module}/../../helm/observability/values-alloy.yaml")]

  set {
    name  = "alloy.extraEnv[0].value"
    value = "http://loki.observability.svc:3100/loki/api/v1/push"
  }

  depends_on = [
    helm_release.loki,
    kubernetes_config_map.alloy_config,
  ]
}

# ── Grafana (log UI) ─────────────────────────────────────

resource "helm_release" "grafana" {
  name       = "grafana"
  namespace  = kubernetes_namespace.observability.metadata[0].name
  repository = "https://grafana.github.io/helm-charts"
  chart      = "grafana"
  version    = "8.14.0"
  wait       = true
  timeout    = var.deploy_timeout

  values = [file("${path.module}/../../helm/observability/values-grafana.yaml")]

  set {
    name  = "datasources.datasources\\.yaml.datasources[0].url"
    value = "http://loki.observability.svc:3100"
  }

  # NodePort for KinD access
  set {
    name  = "service.type"
    value = "NodePort"
  }
  set {
    name  = "service.nodePort"
    value = "30300"
  }

  # TLS when mkcert is available
  dynamic "set" {
    for_each = var.use_mkcert ? [1] : []
    content {
      name  = "grafana\\.ini.server.protocol"
      value = "https"
    }
  }
  dynamic "set" {
    for_each = var.use_mkcert ? [1] : []
    content {
      name  = "grafana\\.ini.server.cert_file"
      value = "/certs/tls.crt"
    }
  }
  dynamic "set" {
    for_each = var.use_mkcert ? [1] : []
    content {
      name  = "grafana\\.ini.server.cert_key"
      value = "/certs/tls.key"
    }
  }
  dynamic "set" {
    for_each = var.use_mkcert ? [1] : []
    content {
      name  = "extraSecretMounts[0].name"
      value = "tls-certs"
    }
  }
  dynamic "set" {
    for_each = var.use_mkcert ? [1] : []
    content {
      name  = "extraSecretMounts[0].secretName"
      value = "kinotic-tls"
    }
  }
  dynamic "set" {
    for_each = var.use_mkcert ? [1] : []
    content {
      name  = "extraSecretMounts[0].mountPath"
      value = "/certs"
    }
  }
  dynamic "set" {
    for_each = var.use_mkcert ? [1] : []
    content {
      name  = "extraSecretMounts[0].readOnly"
      value = "true"
    }
  }

  # Health probes must use HTTPS when TLS is enabled
  dynamic "set" {
    for_each = var.use_mkcert ? [1] : []
    content {
      name  = "readinessProbe.httpGet.scheme"
      value = "HTTPS"
    }
  }
  dynamic "set" {
    for_each = var.use_mkcert ? [1] : []
    content {
      name  = "livenessProbe.httpGet.scheme"
      value = "HTTPS"
    }
  }

  depends_on = [
    helm_release.loki,
    kubernetes_secret.observability_tls,
  ]
}
