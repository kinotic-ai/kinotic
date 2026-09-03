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
    # Where the SPA lives — used for verification email links and post-OIDC SPA redirects.
    { name = "kinotic.domain.appBaseUrl", value = "https://portal.${local.global.dns_zone_name}" },
    # Where the backend lives — used as the OIDC redirect_uri so the IdP returns the
    # browser to the AKS-hosted /api/{login,signup}/callback/* path, not the SPA's domain.
    { name = "kinotic.domain.apiBaseUrl", value = "https://api.${local.global.dns_zone_name}" },
    # Workload identity for Azure Key Vault access
    { name = "workloadIdentity.enabled", value = "true" },
    { name = "workloadIdentity.clientId", value = azurerm_user_assigned_identity.kinotic_server.client_id },
    # Cluster Key Vault for tenant/app secrets
    { name = "extraEnv.KINOTIC_DOMAIN_SECRET_STORAGE_BACKEND", value = "azure" },
    { name = "extraEnv.KINOTIC_DOMAIN_SECRET_STORAGE_AZURE_VAULT_URL", value = azurerm_key_vault.main.vault_uri },
    # Organization storage — where kinotic-server provisions each organization's account
    { name = "extraEnv.KINOTIC_MANAGEMENTAPI_ORGANIZATIONSTORAGE_SUBSCRIPTIONIDS", value = data.azurerm_client_config.keyvault.subscription_id },
    { name = "extraEnv.KINOTIC_MANAGEMENTAPI_ORGANIZATIONSTORAGE_RESOURCEGROUP", value = azurerm_resource_group.org_storage.name },
    { name = "extraEnv.KINOTIC_MANAGEMENTAPI_ORGANIZATIONSTORAGE_LOCATION", value = var.location },
    { name = "extraEnv.KINOTIC_MANAGEMENTAPI_ORGANIZATIONSTORAGE_PRIVATEENDPOINTSUBNETID", value = azurerm_subnet.private_endpoints.id },
    { name = "extraEnv.KINOTIC_MANAGEMENTAPI_ORGANIZATIONSTORAGE_PRIVATEDNSZONEID", value = azurerm_private_dns_zone.blob.id },
    # UI sites — the Front Door profile and DNS zone kinotic-server serves published UIs from
    { name = "extraEnv.KINOTIC_MANAGEMENTAPI_UIDEPLOYMENT_SITESDOMAIN", value = local.sites_domain },
    { name = "extraEnv.KINOTIC_MANAGEMENTAPI_UIDEPLOYMENT_DNSZONEID", value = local.global.dns_zone_id },
    { name = "extraEnv.KINOTIC_MANAGEMENTAPI_UIDEPLOYMENT_FRONTDOORPROFILEID", value = azurerm_cdn_frontdoor_profile.sites.id },
    { name = "extraEnv.KINOTIC_MANAGEMENTAPI_UIDEPLOYMENT_FRONTDOORENDPOINTHOSTNAME", value = azurerm_cdn_frontdoor_endpoint.sites.host_name },
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
    azurerm_role_assignment.kinotic_server_org_storage_accounts,
    azurerm_role_assignment.kinotic_server_org_storage_blobs,
    azurerm_role_assignment.kinotic_server_org_storage_network,
    azurerm_role_assignment.kinotic_server_private_endpoint_subnet,
    azurerm_role_assignment.kinotic_server_blob_private_dns,
    azurerm_private_dns_zone_virtual_network_link.blob,
    azurerm_role_assignment.kinotic_server_frontdoor,
    azurerm_role_assignment.kinotic_server_sites_dns,
    azurerm_federated_identity_credential.kinotic_server,
  ]
}
