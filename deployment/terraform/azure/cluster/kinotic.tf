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
    # SPA is hosted on Azure Storage (Static Web Apps) — no static server inside the cluster.
    { name = "kinotic.webServer.enabled", value = "false" },
    # Public URL for OIDC redirect_uri + email links
    { name = "kinotic.appBaseUrl", value = "https://portal.${local.global.dns_zone_name}" },
    # Workload identity for Azure Key Vault access
    { name = "workloadIdentity.enabled", value = "true" },
    { name = "workloadIdentity.clientId", value = azurerm_user_assigned_identity.kinotic_server.client_id },
    # Cluster Key Vault for tenant/app secrets
    { name = "extraEnv.KINOTIC_SECRET_STORAGE_BACKEND", value = "azure" },
    { name = "extraEnv.KINOTIC_SECRET_STORAGE_AZURE_VAULT_URL", value = azurerm_key_vault.main.vault_uri },
    # Email (Azure Communication Services) — shared service from global terraform
    { name = "extraEnv.KINOTIC_EMAIL_BACKEND", value = "azure" },
    { name = "extraEnv.KINOTIC_EMAIL_AZURE_ENDPOINT", value = local.global.email_service_endpoint },
    { name = "extraEnv.KINOTIC_EMAIL_AZURE_SENDER_DOMAIN", value = local.global.email_sender_domain },
    # Platform secrets (JWT signing + secret-storage masterKey) from the global Key Vault
    { name = "platformSecrets.keyVault.name", value = local.global.platform_key_vault_name },
    { name = "platformSecrets.keyVault.tenantId", value = local.global.tenant_id },
    # OIDC client secrets — same vault, separate object set keyed by configId
    { name = "oidcSecrets.keyVault.name", value = local.global.platform_key_vault_name },
    { name = "oidcSecrets.keyVault.tenantId", value = local.global.tenant_id },
    { name = "oidcSecrets.objects[0]", value = "entra-platform" },
    # Platform OIDC provider — wires the Entra app from global terraform
    { name = "kinotic.oidc.platformProviders[0].id", value = "entra-platform" },
    { name = "kinotic.oidc.platformProviders[0].name", value = "Microsoft" },
    { name = "kinotic.oidc.platformProviders[0].provider", value = "azure-ad" },
    { name = "kinotic.oidc.platformProviders[0].clientId", value = local.global.kinotic_oidc_client_id },
    { name = "kinotic.oidc.platformProviders[0].authority", value = local.global.kinotic_oidc_authority },
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
