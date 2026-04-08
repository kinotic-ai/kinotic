# ── Observability: Loki + Alloy + Grafana ─────────────────────────────────────

resource "kubernetes_namespace" "observability" {
  metadata {
    name   = "observability"
    labels = { "app.kubernetes.io/managed-by" = "terraform" }
  }
  depends_on = [module.aks]
}

# ── Copy TLS cert to observability namespace ──────────────────────────────────
# The Let's Encrypt cert lives in kinotic namespace. Grafana needs it here.

resource "terraform_data" "grafana_tls_copy" {
  provisioner "local-exec" {
    command = <<-EOT
      set -e
      echo "Copying TLS secret to observability namespace..."
      for i in $(seq 1 30); do
        DATA=$(kubectl get secret ${var.tls_secret_name} -n kinotic \
          -o jsonpath='{.data.tls\.crt}' 2>/dev/null || true)
        if [ -n "$DATA" ]; then
          kubectl get secret ${var.tls_secret_name} -n kinotic -o json \
            | jq 'del(.metadata.namespace,.metadata.resourceVersion,.metadata.uid,.metadata.creationTimestamp,.metadata.ownerReferences,.metadata.managedFields)' \
            | jq '.metadata.namespace = "observability"' \
            | kubectl apply -n observability -f -
          echo "TLS secret copied to observability"
          exit 0
        fi
        echo "  Attempt $i/30 — waiting for TLS secret..."
        sleep 5
      done
      echo "ERROR: TLS secret not found"
      exit 1
    EOT
  }

  depends_on = [
    terraform_data.tls_cert_ready,
    kubernetes_namespace.observability,
  ]
}

# ── Loki (log storage with Azure Blob backend) ───────────────────────────────

resource "helm_release" "loki" {
  name       = "loki"
  namespace  = kubernetes_namespace.observability.metadata[0].name
  repository = "https://grafana.github.io/helm-charts"
  chart      = "loki"
  version    = "6.29.0"
  wait       = true
  timeout    = 300

  values = [
    file("${path.module}/../../helm/observability/values-loki.yaml"),
    file("${path.module}/../../helm/observability/values-loki-azure.yaml"),
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
    "config.alloy" = file("${path.module}/../../helm/observability/alloy-config.alloy")
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

# ── Grafana Entra ID App Registration (conditional) ──────────────────────────
# Creates the Azure AD application, client secret, and redirect URIs
# so Grafana can authenticate users via Entra ID. No portal clicks needed.

resource "azuread_application" "grafana" {
  count        = !var.disable_grafana_entra ? 1 : 0
  display_name = "${local.name_prefix}-grafana"

  web {
    redirect_uris = [
      "https://grafana.${var.domain_name}/login/generic_oauth",
      "https://grafana.${var.domain_name}/login/azuread",
    ]

    implicit_grant {
      access_token_issuance_enabled = false
      id_token_issuance_enabled     = true
    }
  }

  required_resource_access {
    # Microsoft Graph
    resource_app_id = "00000003-0000-0000-c000-000000000000"

    resource_access {
      # User.Read
      id   = "e1fe6dd8-ba31-4d61-89e7-88639da4683d"
      type = "Scope"
    }
    resource_access {
      # email
      id   = "64a6cdd6-aab1-4aaf-94b8-3cc8405e90d0"
      type = "Scope"
    }
    resource_access {
      # openid
      id   = "37f7f235-527c-4136-accd-4a02d197296e"
      type = "Scope"
    }
    resource_access {
      # profile
      id   = "14dad69e-099b-42c9-810b-d002981feec1"
      type = "Scope"
    }
  }

  optional_claims {
    id_token {
      name = "email"
    }
    id_token {
      name = "preferred_username"
    }
  }
}

resource "azuread_application_password" "grafana" {
  count          = !var.disable_grafana_entra ? 1 : 0
  application_id = azuread_application.grafana[0].id
  display_name   = "grafana-terraform"
}

# ── Grafana (log UI) ──────────────────────────────────────────────────────────

resource "helm_release" "grafana" {
  name       = "grafana"
  namespace  = kubernetes_namespace.observability.metadata[0].name
  repository = "https://grafana.github.io/helm-charts"
  chart      = "grafana"
  version    = "8.14.0"
  wait       = true
  timeout    = 300

  values = concat(
    [file("${path.module}/../../helm/observability/values-grafana.yaml")],
    !var.disable_grafana_entra ? [file("${path.module}/../../helm/observability/values-grafana-azure.yaml")] : [],
  )

  set {
    name  = "datasources.datasources\\.yaml.datasources[0].url"
    value = "http://loki.observability.svc:3100"
  }

  # Entra ID OAuth (when enabled)
  dynamic "set" {
    for_each = !var.disable_grafana_entra ? [1] : []
    content {
      name  = "grafana\\.ini.auth\\.azuread.client_id"
      value = azuread_application.grafana[0].client_id
    }
  }

  dynamic "set" {
    for_each = !var.disable_grafana_entra ? [1] : []
    content {
      name  = "grafana\\.ini.auth\\.azuread.tenant_id"
      value = data.azurerm_client_config.tls.tenant_id
    }
  }

  dynamic "set" {
    for_each = !var.disable_grafana_entra ? [1] : []
    content {
      name  = "grafana\\.ini.auth\\.azuread.auth_url"
      value = "https://login.microsoftonline.com/${data.azurerm_client_config.tls.tenant_id}/oauth2/v2.0/authorize"
    }
  }

  dynamic "set" {
    for_each = !var.disable_grafana_entra ? [1] : []
    content {
      name  = "grafana\\.ini.auth\\.azuread.token_url"
      value = "https://login.microsoftonline.com/${data.azurerm_client_config.tls.tenant_id}/oauth2/v2.0/token"
    }
  }

  dynamic "set_sensitive" {
    for_each = !var.disable_grafana_entra ? [1] : []
    content {
      name  = "grafana\\.ini.auth\\.azuread.client_secret"
      value = azuread_application_password.grafana[0].value
    }
  }

  # Health probes must use HTTPS
  set {
    name  = "readinessProbe.httpGet.scheme"
    value = "HTTPS"
  }
  set {
    name  = "livenessProbe.httpGet.scheme"
    value = "HTTPS"
  }

  depends_on = [
    helm_release.loki,
    terraform_data.grafana_tls_copy,
  ]
}
