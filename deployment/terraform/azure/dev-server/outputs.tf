output "api_hostname" {
  description = "The API's hostname: what the router's public address answers to, and the one certbot issues for"
  value       = local.api_hostname
}

output "portal_hostname" {
  description = "The portal's hostname, served by Front Door from sites/<hostname>/ in the sites account"
  value       = module.environment.ui_hostnames.portal
}

output "console_hostname" {
  description = "The system console's hostname, served by Front Door from sites/<hostname>/ in the sites account"
  value       = module.environment.ui_hostnames.console
}

output "sites_domain" {
  description = "The domain every published UI is a label under"
  value       = module.environment.sites_domain
}

output "sites_storage_blob_endpoint" {
  description = "The blob endpoint the server publishes sites into"
  value       = module.environment.sites_storage_blob_endpoint
}

output "server_key_vault_url" {
  description = "Where the server's secret storage lives (kinotic.domain.secretStorage.azure.vaultUrl)"
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
  description = "The shared Communication Services endpoint the server sends mail through"
  value       = local.global.email_service_endpoint
}

output "email_sender_address" {
  description = "The address mail is sent from"
  value       = "DoNotReply@${local.global.email_sender_domain}"
}

output "server_client_id" {
  description = "The service principal the server runs as (AZURE_CLIENT_ID)"
  value       = module.environment.server_client_id
}

output "tenant_id" {
  description = "The tenant the principal lives in (AZURE_TENANT_ID)"
  value       = module.environment.tenant_id
}

# The proxmox root merges this into kinotic-server's environment, with the addresses only it
# knows. Nothing here is secret.
output "dev_server_env" {
  description = "The non-secret half of the server's environment"
  value = {
    DEV_SERVER_API_HOSTNAME                             = local.api_hostname
    DEV_SERVER_PORTAL_HOSTNAME                          = module.environment.ui_hostnames.portal
    DEV_SERVER_CONSOLE_HOSTNAME                         = module.environment.ui_hostnames.console
    KINOTIC_SYSTEMAPI_UIDEPLOYMENT_SITESDOMAIN          = module.environment.sites_domain
    KINOTIC_SYSTEMAPI_UIDEPLOYMENT_SITESSTORAGEENDPOINT = module.environment.sites_storage_blob_endpoint
    KINOTIC_DOMAIN_SECRETSTORAGE_AZURE_VAULTURL         = azurerm_key_vault.server.vault_uri
    KINOTIC_DOMAIN_EMAIL_ENDPOINT                       = local.global.email_service_endpoint
    KINOTIC_DOMAIN_EMAIL_SENDERADDRESS                  = "DoNotReply@${local.global.email_sender_domain}"
    AZURE_CLIENT_ID                                     = module.environment.server_client_id
    AZURE_TENANT_ID                                     = module.environment.tenant_id
  }
}

# The operator places this on the host as kinotic-server.env (sync-secrets.sh in the proxmox
# root); it never goes through that root or its state.
output "secrets_env" {
  description = "The Azure half of the server's secrets, as an env-file line"
  value       = "AZURE_CLIENT_SECRET=${module.environment.server_client_secret}\n"
  sensitive   = true
}

output "resource_group_name" {
  description = "The resource group, for deploy-ui.sh"
  value       = module.environment.resource_group_name
}

output "sites_frontdoor_profile" {
  description = "The Front Door profile serving the sites account, for deploy-ui.sh's purge"
  value       = module.environment.sites_frontdoor_profile_name
}

output "sites_frontdoor_endpoint" {
  description = "Its endpoint, for deploy-ui.sh's purge"
  value       = module.environment.sites_frontdoor_endpoint_name
}
