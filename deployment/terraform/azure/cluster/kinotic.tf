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
    # Where the portal and the console live — post-OIDC redirects and the emailed links — and
    # where each server's REST endpoints live, the OIDC redirect_uri
    { name = "kinotic.domain.portalBaseUrl", value = "https://portal.${local.global.dns_zone_name}" },
    { name = "kinotic.managementApi.apiBaseUrl", value = "https://api.${local.global.dns_zone_name}" },
    # The Turnstile site the portal's find-my-organizations form presents; the management server verifies its
    # tokens with the secret the global root stores
    { name = "servers.kinotic-server-management.managementServer.turnstile.siteKey", value = var.turnstile_site_key },
    { name = "servers.kinotic-server-system.systemServer.consoleBaseUrl", value = "https://console.${local.global.dns_zone_name}" },
    { name = "servers.kinotic-server-system.systemServer.apiBaseUrl", value = "https://system-api.${local.global.dns_zone_name}" },
    # Every application's API host, <organizationId>--<applicationId>.apps-api.<cloudflare zone>
    { name = "kinotic.domain.appApiBaseUrl", value = "https://${local.apps_api_domain}" },
    # What a workload dials: the management server for sync, the app server for runtime
    { name = "servers.kinotic-server-system.extraEnv.KINOTIC_SYSTEMAPI_DEPLOYMENT_MANAGEMENTSERVER_HOST", value = "api.${local.global.dns_zone_name}" },
    { name = "servers.kinotic-server-system.extraEnv.KINOTIC_SYSTEMAPI_DEPLOYMENT_MANAGEMENTSERVER_PORT", value = "443" },
    { name = "servers.kinotic-server-system.extraEnv.KINOTIC_SYSTEMAPI_DEPLOYMENT_MANAGEMENTSERVER_USESSL", value = "true" },
    { name = "servers.kinotic-server-system.extraEnv.KINOTIC_SYSTEMAPI_DEPLOYMENT_APPSERVER_HOST", value = local.apps_api_domain },
    { name = "servers.kinotic-server-system.extraEnv.KINOTIC_SYSTEMAPI_DEPLOYMENT_APPSERVER_PORT", value = "443" },
    { name = "servers.kinotic-server-system.extraEnv.KINOTIC_SYSTEMAPI_DEPLOYMENT_APPSERVER_USESSL", value = "true" },
    # Workload identity for Azure Key Vault access
    { name = "workloadIdentity.enabled", value = "true" },
    { name = "workloadIdentity.clientId", value = azurerm_user_assigned_identity.kinotic_server.client_id },
    # Cluster Key Vault for tenant/app secrets
    { name = "extraEnv.KINOTIC_DOMAIN_SECRETSTORAGE_BACKEND", value = "AZURE" },
    { name = "extraEnv.KINOTIC_DOMAIN_SECRETSTORAGE_AZURE_VAULTURL", value = azurerm_key_vault.main.vault_uri },
    # UI sites — the account the system server publishes them into; the domain they are served
    # under is KINOTIC_DOMAIN_PUBLISHEDSITES_DOMAIN below
    { name = "servers.kinotic-server-system.extraEnv.KINOTIC_SYSTEMAPI_UIDEPLOYMENT_SITESSTORAGEENDPOINT", value = module.sites.storage_blob_endpoint },
    # Each organization's sites subdomain: every server knows the domain, whose pages an application's
    # login admits; the management server creates an organization's record at sign-up and the system
    # server before its first publish, with the DNS token the global root stores, so the Cloudflare
    # provisioner runs on those two alone
    { name = "extraEnv.KINOTIC_DOMAIN_PUBLISHEDSITES_DOMAIN", value = local.cloudflare_sites_domain },
    { name = "servers.kinotic-server-management.extraEnv.KINOTIC_DOMAIN_PUBLISHEDSITES_CLOUDFLARE_ENABLED", value = "true" },
    { name = "servers.kinotic-server-management.extraEnv.KINOTIC_DOMAIN_PUBLISHEDSITES_CLOUDFLARE_ZONEID", value = var.cloudflare_zone_id },
    { name = "servers.kinotic-server-system.extraEnv.KINOTIC_DOMAIN_PUBLISHEDSITES_CLOUDFLARE_ENABLED", value = "true" },
    { name = "servers.kinotic-server-system.extraEnv.KINOTIC_DOMAIN_PUBLISHEDSITES_CLOUDFLARE_ZONEID", value = var.cloudflare_zone_id },
    # Email (Azure Communication Services) — shared service from global terraform, as the
    # servers' workload identity
    { name = "kinotic.domain.email.enabled", value = "true" },
    { name = "kinotic.domain.email.endpoint", value = local.global.email_service_endpoint },
    { name = "kinotic.domain.email.senderAddress", value = "DoNotReply@${local.global.email_sender_domain}" },
    { name = "kinotic.domain.email.managedIdentityClientId", value = azurerm_user_assigned_identity.kinotic_server.client_id },
    # System secrets (each server's JWT signing keys, the secret-storage master key, the GitHub
    # App's secrets) from the global Key Vault
    { name = "systemSecrets.keyVault.name", value = local.global.platform_key_vault_name },
    { name = "systemSecrets.keyVault.tenantId", value = local.global.tenant_id },
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
