output "api_hostname" {
  description = "The management server's hostname, on the router's public address: the API of the portal, the CLI and MCP hosts"
  value       = local.api_hostname
}

output "system_api_hostname" {
  description = "The system server's hostname, on the router's public address: the system console's API"
  value       = local.system_api_hostname
}

output "summit42_hostname" {
  description = "Summit 42's UI, served by the sites Worker from sites/<hostname>/ in the sites account, or null when summit42_enabled is false; redeploy.sh in the proxmox root uploads it"
  value       = local.summit42_hostname
}

output "summit42_api_hostname" {
  description = "The Summit 42 server's hostname, on the router's public address, or null when summit42_enabled is false"
  value       = local.summit42_api_hostname
}

output "apps_api_domain" {
  description = "The app server's hostname in the Cloudflare zone, proxied to the router's public address, which runtime workloads dial; every application's API host is a label under it"
  value       = local.apps_api_domain
}

output "portal_hostname" {
  description = "The portal's hostname in the platform zone, served by the sites Worker from sites/<hostname>/ in the sites account; deploy-ui.sh uploads it"
  value       = local.portal_hostname
}

output "console_hostname" {
  description = "The system console's hostname, served the same way"
  value       = local.console_hostname
}

output "sites_domain" {
  description = "The domain every organization's sites are under: <app>--<ui>.<org>.<sites domain>"
  value       = module.environment.sites_domain
}

output "sites_storage_blob_endpoint" {
  description = "The blob endpoint the server publishes sites into"
  value       = module.environment.sites_storage_blob_endpoint
}

output "server_key_vault_url" {
  description = "Where the servers' secret storage lives (kinotic.domain.secretStorage.azure.vaultUrl)"
  value       = azurerm_key_vault.server.vault_uri
}

output "snapshots_storage_account_name" {
  description = "The storage account the Elasticsearch snapshot repository writes to"
  value       = azurerm_storage_account.snapshots.name
}

output "snapshots_storage_account_key" {
  description = "Its key, for the Elasticsearch keystore (azure.client.default.key)"
  value       = azurerm_storage_account.snapshots.primary_access_key
  sensitive   = true
}

output "snapshots_container" {
  description = "The container the repository is registered on"
  value       = azurerm_storage_container.snapshots.name
}

output "email_endpoint" {
  description = "The shared Communication Services endpoint the servers send mail through"
  value       = local.global.email_service_endpoint
}

output "email_sender_address" {
  description = "The address mail is sent from"
  value       = "DoNotReply@${local.global.email_sender_domain}"
}

output "server_client_id" {
  description = "The service principal the servers run as (AZURE_CLIENT_ID)"
  value       = module.environment.server_client_id
}

output "tenant_id" {
  description = "The tenant the principal lives in (AZURE_TENANT_ID)"
  value       = module.environment.tenant_id
}

# The proxmox root merges this into every server's environment, with the addresses only it
# knows. Nothing here is secret.
output "dev_server_env" {
  description = "The non-secret half of the servers' environment"
  value = {
    # the portal, the trusted UIs and Summit 42 as regex alternatives, the management server's CORS pattern
    DEV_SERVER_MANAGEMENT_UI_HOSTNAMES                  = join("|", [for hostname in compact(concat([local.portal_hostname], var.trusted_ui_hostnames, [local.summit42_hostname])) : replace(hostname, ".", "\\.")])
    DEV_SERVER_CONSOLE_HOSTNAME                         = local.console_hostname
    KINOTIC_SYSTEMAPI_UIDEPLOYMENT_SITESSTORAGEENDPOINT = module.environment.sites_storage_blob_endpoint
    KINOTIC_DOMAIN_PUBLISHEDSITES_DOMAIN                = module.environment.sites_domain
    KINOTIC_DOMAIN_PUBLISHEDSITES_CLOUDFLARE_ENABLED    = "true"
    KINOTIC_DOMAIN_PUBLISHEDSITES_CLOUDFLARE_ZONEID     = var.cloudflare_zone_id
    KINOTIC_DOMAIN_SECRETSTORAGE_AZURE_VAULTURL         = azurerm_key_vault.server.vault_uri
    KINOTIC_DOMAIN_EMAIL_ENDPOINT                       = local.global.email_service_endpoint
    KINOTIC_DOMAIN_EMAIL_SENDERADDRESS                  = "DoNotReply@${local.global.email_sender_domain}"
    AZURE_CLIENT_ID                                     = module.environment.server_client_id
    AZURE_TENANT_ID                                     = module.environment.tenant_id
  }
}

# The operator places this on the host as kinotic-servers.env (sync-secrets.sh in the proxmox
# root), which every server's environment merges; it never goes through that root or its state.
output "secrets_env" {
  description = "The Azure and Cloudflare half of the servers' secrets, as env-file lines"
  value       = "AZURE_CLIENT_SECRET=${module.environment.server_client_secret}\nKINOTIC_DOMAIN_PUBLISHEDSITES_CLOUDFLARE_APITOKEN=${var.cloudflare_dns_api_token}\n"
  sensitive   = true
}
