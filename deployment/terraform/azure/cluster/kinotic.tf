# ── ES Secret Sync ────────────────────────────────────────────────────────────

resource "helm_release" "es_secret_sync" {
  name      = "es-secret-sync"
  namespace = kubernetes_namespace.kinotic.metadata[0].name
  chart     = "${path.module}/../../../helm/es-secret-sync"
  wait      = true
  timeout   = 300

  depends_on = [helm_release.eck_stack, kubernetes_namespace.kinotic]
}

# ── Kinotic servers ───────────────────────────────────────────────────────────
# The org, system and app servers in one release, each exposed on 443 by a LoadBalancer of its
# own (dns.tf gives each its names) and terminating TLS with the cluster certificate, which
# carries every server's names (tls.tf).

resource "helm_release" "kinotic" {
  name      = "kinotic"
  namespace = kubernetes_namespace.kinotic.metadata[0].name
  chart     = "${path.module}/../../../helm/kinotic"
  wait      = true
  timeout   = 900

  values = [
    file("${path.module}/../../../helm/kinotic/values.yaml"),
    file("${path.module}/config/kinotic/values.yaml"),
  ]

  set = [
    { name = "tls.enabled", value = "true" },
    { name = "servers.kinotic-server-management.tlsSecretName", value = var.tls_secret_name },
    { name = "servers.kinotic-server-system.tlsSecretName", value = var.tls_secret_name },
    { name = "servers.kinotic-server-app.tlsSecretName", value = var.tls_secret_name },
    { name = "image.tag", value = var.kinotic_version },
    # The portal is hosted outside the cluster — no static server inside.
    { name = "servers.kinotic-server-management.webServer.enabled", value = "false" },
    # Where the portal and the console live — post-OIDC redirects and the emailed links — and
    # where each server's REST endpoints live, the OIDC redirect_uri
    { name = "servers.kinotic-server-management.orgServer.portalBaseUrl", value = "https://portal.${local.global.dns_zone_name}" },
    { name = "servers.kinotic-server-management.orgServer.apiBaseUrl", value = "https://api.${local.global.dns_zone_name}" },
    { name = "servers.kinotic-server-system.systemServer.consoleBaseUrl", value = "https://console.${local.global.dns_zone_name}" },
    { name = "servers.kinotic-server-system.systemServer.apiBaseUrl", value = "https://system-api.${local.global.dns_zone_name}" },
    # Every application's API host, <organizationId>--<applicationId>.apps-api.<zone>: what the app
    # server serves, and what the system server hands each UI build
    { name = "servers.kinotic-server-app.appServer.apiBaseUrl", value = "https://apps-api.${local.global.dns_zone_name}" },
    { name = "servers.kinotic-server-system.deployment.appApiBaseUrl", value = "https://apps-api.${local.global.dns_zone_name}" },
    # What a workload dials: the org server for sync, the app server for runtime
    { name = "servers.kinotic-server-system.extraEnv.KINOTIC_SYSTEMAPI_DEPLOYMENT_ORGSERVER_HOST", value = "api.${local.global.dns_zone_name}" },
    { name = "servers.kinotic-server-system.extraEnv.KINOTIC_SYSTEMAPI_DEPLOYMENT_ORGSERVER_PORT", value = "443" },
    { name = "servers.kinotic-server-system.extraEnv.KINOTIC_SYSTEMAPI_DEPLOYMENT_ORGSERVER_USESSL", value = "true" },
    { name = "servers.kinotic-server-system.extraEnv.KINOTIC_SYSTEMAPI_DEPLOYMENT_APPSERVER_HOST", value = "apps-api.${local.global.dns_zone_name}" },
    { name = "servers.kinotic-server-system.extraEnv.KINOTIC_SYSTEMAPI_DEPLOYMENT_APPSERVER_PORT", value = "443" },
    { name = "servers.kinotic-server-system.extraEnv.KINOTIC_SYSTEMAPI_DEPLOYMENT_APPSERVER_USESSL", value = "true" },
    # Workload identity for Azure Key Vault access
    { name = "workloadIdentity.enabled", value = "true" },
    { name = "workloadIdentity.clientId", value = azurerm_user_assigned_identity.kinotic_server.client_id },
    # Cluster Key Vault for tenant/app secrets
    { name = "extraEnv.KINOTIC_DOMAIN_SECRETSTORAGE_BACKEND", value = "AZURE" },
    { name = "extraEnv.KINOTIC_DOMAIN_SECRETSTORAGE_AZURE_VAULTURL", value = azurerm_key_vault.main.vault_uri },
    # UI sites — the domain published UIs are served under and the account the system server
    # publishes them into
    { name = "servers.kinotic-server-system.extraEnv.KINOTIC_SYSTEMAPI_UIDEPLOYMENT_SITESDOMAIN", value = local.sites_domain },
    { name = "servers.kinotic-server-system.extraEnv.KINOTIC_SYSTEMAPI_UIDEPLOYMENT_SITESSTORAGEENDPOINT", value = module.sites.storage_blob_endpoint },
    # Email (Azure Communication Services) — shared service from global terraform, as the
    # servers' workload identity
    { name = "kinotic.domain.email.enabled", value = "true" },
    { name = "kinotic.domain.email.endpoint", value = local.global.email_service_endpoint },
    { name = "kinotic.domain.email.senderAddress", value = "DoNotReply@${local.global.email_sender_domain}" },
    { name = "kinotic.domain.email.managedIdentityClientId", value = azurerm_user_assigned_identity.kinotic_server.client_id },
    # Platform secrets (each server's JWT signing keys, the secret-storage master key, the GitHub
    # App's secrets) from the global Key Vault
    { name = "platformSecrets.keyVault.name", value = local.global.platform_key_vault_name },
    { name = "platformSecrets.keyVault.tenantId", value = local.global.tenant_id },
  ]

  depends_on = [
    helm_release.eck_stack,
    helm_release.es_secret_sync,
    terraform_data.tls_cert_ready,
    helm_release.reloader,
    azurerm_role_assignment.kinotic_server_kv_secrets,
    azurerm_role_assignment.kinotic_server_email_contributor,
    module.sites,
    azurerm_federated_identity_credential.kinotic_server,
  ]
}

moved {
  from = helm_release.kinotic_server
  to   = helm_release.kinotic
}
