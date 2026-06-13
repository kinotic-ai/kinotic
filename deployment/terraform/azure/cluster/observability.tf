# ── Observability: Loki + Alloy + Grafana ─────────────────────────────────────

resource "kubernetes_namespace" "observability" {
  metadata {
    name   = "observability"
    labels = { "app.kubernetes.io/managed-by" = "terraform" }
  }
  depends_on = [module.aks]
}

# ── Loki (log storage with Azure Blob backend) ───────────────────────────────

resource "helm_release" "loki" {
  name       = "loki"
  namespace  = kubernetes_namespace.observability.metadata[0].name
  repository = "https://grafana.github.io/helm-charts"
  chart      = "loki"
  version    = "6.29.0"
  wait       = true
  timeout    = 600

  values = [
    file("${path.module}/../../../helm/observability/values-loki.yaml"),
  ]

  depends_on = [kubernetes_namespace.observability]
}

# ── Alloy config (ConfigMap) ──────────────────────────────────────────────────

resource "kubernetes_config_map" "alloy_config" {
  metadata {
    name      = "alloy-config"
    namespace = kubernetes_namespace.observability.metadata[0].name
  }

  data = {
    "config.alloy" = file("${path.module}/../../../helm/observability/alloy-config.alloy")
  }

  depends_on = [kubernetes_namespace.observability]
}

# ── Alloy (log collector, DaemonSet) ──────────────────────────────────────────

resource "helm_release" "alloy" {
  name       = "alloy"
  namespace  = kubernetes_namespace.observability.metadata[0].name
  repository = "https://grafana.github.io/helm-charts"
  chart      = "alloy"
  version    = "0.12.0"
  wait       = true
  timeout    = 300

  values = [file("${path.module}/../../../helm/observability/values-alloy.yaml")]

  set = [
    { name = "alloy.extraEnv[0].value", value = "http://loki.observability.svc:3100/loki/api/v1/push" },
  ]

  depends_on = [
    helm_release.loki,
    kubernetes_config_map.alloy_config,
  ]
}

# ── Grafana (log UI) ──────────────────────────────────────────────────────────

locals {
  grafana_entra_enabled = local.global.grafana_entra_client_id != ""
}

resource "helm_release" "grafana" {
  name       = "grafana"
  namespace  = kubernetes_namespace.observability.metadata[0].name
  repository = "https://grafana.github.io/helm-charts"
  chart      = "grafana"
  version    = "8.14.0"
  wait       = true
  timeout    = 300

  values = concat(
    [file("${path.module}/../../../helm/observability/values-grafana.yaml")],
    local.grafana_entra_enabled ? [file("${path.module}/../../../helm/observability/values-grafana-azure.yaml")] : [],
  )

  # Entra ID OAuth (when enabled)
  set = concat(
    [
      { name = "datasources.datasources\\.yaml.datasources[0].url", value = "http://loki.observability.svc:3100" },
      { name = "assertNoLeakedSecrets", value = "false" },
    ],
    local.grafana_entra_enabled ? [
      { name = "grafana\\.ini.auth\\.azuread.client_id", value = local.global.grafana_entra_client_id },
      { name = "grafana\\.ini.auth\\.azuread.tenant_id", value = local.global.tenant_id },
      { name = "grafana\\.ini.auth\\.azuread.auth_url", value = "https://login.microsoftonline.com/${local.global.tenant_id}/oauth2/v2.0/authorize" },
      { name = "grafana\\.ini.auth\\.azuread.token_url", value = "https://login.microsoftonline.com/${local.global.tenant_id}/oauth2/v2.0/token" },
    ] : [],
  )

  set_sensitive = local.grafana_entra_enabled ? [
    { name = "grafana\\.ini.auth\\.azuread.client_secret", value = local.global.grafana_entra_client_secret },
  ] : []

  depends_on = [helm_release.loki]
}
