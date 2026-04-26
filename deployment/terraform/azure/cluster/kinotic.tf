# ── ES Secret Sync ────────────────────────────────────────────────────────────

resource "helm_release" "es_secret_sync" {
  name      = "es-secret-sync"
  namespace = kubernetes_namespace.kinotic.metadata[0].name
  chart     = "${path.module}/../../../helm/es-secret-sync"
  wait      = true
  timeout   = 300

  depends_on = [helm_release.eck_stack, kubernetes_namespace.kinotic]
}

# ── Kinotic Server ────────────────────────────────────────────────────────────

resource "helm_release" "kinotic_server" {
  name      = "kinotic-server"
  namespace = kubernetes_namespace.kinotic.metadata[0].name
  chart     = "${path.module}/../../../helm/kinotic"
  wait      = true
  timeout   = 900

  values = [
    file("${path.module}/../../../helm/kinotic/values.yaml"),
    file("${path.module}/config/kinotic-server/values.yaml"),
  ]

  set = [
    { name = "tls.enabled", value = "true" },
    { name = "tls.secretName", value = var.tls_secret_name },
    { name = "image.tag", value = var.kinotic_version },
    { name = "migration.image.tag", value = var.kinotic_version },
    # Workload identity for Azure Key Vault access
    { name = "workloadIdentity.enabled", value = "true" },
    { name = "workloadIdentity.clientId", value = azurerm_user_assigned_identity.kinotic_server.client_id },
    # Key Vault config
    { name = "extraEnv.KINOTIC_SECRET_STORAGE_BACKEND", value = "azure" },
    { name = "extraEnv.KINOTIC_SECRET_STORAGE_AZURE_VAULT_URL", value = azurerm_key_vault.main.vault_uri },
    # Email (Azure Communication Services) — shared service from global terraform
    { name = "extraEnv.KINOTIC_EMAIL_BACKEND", value = "azure" },
    { name = "extraEnv.KINOTIC_EMAIL_AZURE_ENDPOINT", value = local.global.email_service_endpoint },
    { name = "extraEnv.KINOTIC_EMAIL_AZURE_SENDER_DOMAIN", value = local.global.email_sender_domain },
  ]

  depends_on = [
    helm_release.eck_stack,
    helm_release.es_secret_sync,
    terraform_data.tls_cert_ready,
    helm_release.reloader,
    azurerm_role_assignment.kinotic_server_kv_secrets,
    azurerm_role_assignment.kinotic_server_email_contributor,
    azurerm_federated_identity_credential.kinotic_server,
  ]
}
