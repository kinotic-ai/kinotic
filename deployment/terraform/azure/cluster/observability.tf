# ── Observability: Loki + Tempo + Mimir + Alloy + Grafana ────────────────────

resource "kubernetes_namespace" "observability" {
  metadata {
    name   = "observability"
    labels = { "app.kubernetes.io/managed-by" = "terraform" }
  }
  depends_on = [module.aks]
}

# ── Blob storage for Loki, Tempo and Mimir ────────────────────────────────────
# One account holds the three stores' data, each in containers of its own. Each store runs as a
# workload identity of its own, which may read and write only its own containers.

locals {
  # Storage account names allow 3 to 24 lowercase letters and digits
  observability_storage_account_name = substr("st${replace(local.name_prefix, "-", "")}obs", 0, 24)

  # Each store's service account (named after its release) and the containers it owns
  observability_stores = {
    loki  = ["loki-chunks", "loki-ruler"]
    tempo = ["tempo-traces"]
    mimir = ["mimir-blocks", "mimir-ruler"]
  }
  observability_containers = merge([
    for store, containers in local.observability_stores : { for c in containers : c => store }
  ]...)
}

resource "azurerm_storage_account" "observability" {
  name                            = local.observability_storage_account_name
  resource_group_name             = azurerm_resource_group.main.name
  location                        = var.location
  account_kind                    = "StorageV2"
  account_tier                    = "Standard"
  account_replication_type        = "LRS"
  min_tls_version                 = "TLS1_2"
  allow_nested_items_to_be_public = false
  tags                            = local.common_tags
}

resource "azurerm_storage_container" "observability" {
  for_each              = local.observability_containers
  name                  = each.key
  storage_account_id    = azurerm_storage_account.observability.id
  container_access_type = "private"
}

resource "azurerm_user_assigned_identity" "observability" {
  for_each            = local.observability_stores
  name                = "id-${local.name_prefix}-${each.key}"
  location            = var.location
  resource_group_name = azurerm_resource_group.main.name
  tags                = local.common_tags
}

resource "azurerm_role_assignment" "observability_blob" {
  for_each             = local.observability_containers
  scope                = azurerm_storage_container.observability[each.key].resource_manager_id
  role_definition_name = "Storage Blob Data Contributor"
  principal_id         = azurerm_user_assigned_identity.observability[each.value].principal_id
}

resource "azurerm_federated_identity_credential" "observability" {
  for_each                  = local.observability_stores
  name                      = "${each.key}-federated"
  user_assigned_identity_id = azurerm_user_assigned_identity.observability[each.key].id
  audience                  = ["api://AzureADTokenExchange"]
  issuer                    = data.azurerm_kubernetes_cluster.main.oidc_issuer_url
  subject                   = "system:serviceaccount:observability:${each.key}"
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
    file("${path.module}/../../../helm/observability/values-loki-azure.yaml"),
  ]

  set = [
    { name = "loki.storage.azure.accountName", value = azurerm_storage_account.observability.name },
    { name = "serviceAccount.annotations.azure\\.workload\\.identity/client-id", value = azurerm_user_assigned_identity.observability["loki"].client_id },
  ]

  depends_on = [
    kubernetes_namespace.observability,
    azurerm_role_assignment.observability_blob,
    azurerm_federated_identity_credential.observability,
  ]
}

# ── Mimir (metrics storage with Azure Blob backend, multi-tenant) ────────────

resource "helm_release" "mimir" {
  name      = "mimir"
  namespace = kubernetes_namespace.observability.metadata[0].name
  chart     = "${path.module}/../../../helm/mimir"
  wait      = true
  timeout   = 600

  values = [file("${path.module}/../../../helm/mimir/values-azure.yaml")]

  set = [
    { name = "config.blocks_storage.azure.account_name", value = azurerm_storage_account.observability.name },
    { name = "config.ruler_storage.azure.account_name", value = azurerm_storage_account.observability.name },
    { name = "serviceAccount.annotations.azure\\.workload\\.identity/client-id", value = azurerm_user_assigned_identity.observability["mimir"].client_id },
  ]

  depends_on = [
    kubernetes_namespace.observability,
    azurerm_role_assignment.observability_blob,
    azurerm_federated_identity_credential.observability,
  ]
}

# ── Tempo (trace storage with Azure Blob backend; span metrics to Mimir) ─────

resource "helm_release" "tempo" {
  name       = "tempo"
  namespace  = kubernetes_namespace.observability.metadata[0].name
  repository = "https://grafana.github.io/helm-charts"
  chart      = "tempo"
  version    = "1.14.0"
  wait       = true
  timeout    = 600

  values = [
    file("${path.module}/../../../helm/observability/values-tempo.yaml"),
    file("${path.module}/../../../helm/observability/values-tempo-azure.yaml"),
  ]

  set = [
    { name = "tempo.storage.trace.azure.storage_account_name", value = azurerm_storage_account.observability.name },
    { name = "serviceAccount.annotations.azure\\.workload\\.identity/client-id", value = azurerm_user_assigned_identity.observability["tempo"].client_id },
  ]

  depends_on = [
    helm_release.mimir,
    azurerm_role_assignment.observability_blob,
    azurerm_federated_identity_credential.observability,
  ]
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

# ── Grafana (logs, traces, metrics UI) ───────────────────────────────────────

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

  depends_on = [helm_release.loki, helm_release.tempo, helm_release.mimir]
}
