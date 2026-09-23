output "resource_group_name" {
  description = "The resource group everything here, and every organization storage account, lives in"
  value       = azurerm_resource_group.main.name
}

output "sites_domain" {
  description = "The domain every published UI is a label under"
  value       = module.sites.sites_domain
}

output "sites_storage_blob_endpoint" {
  description = "The blob endpoint kinotic-server publishes sites into"
  value       = module.sites.storage_blob_endpoint
}

output "sites_endpoint_host_name" {
  description = "The Front Door endpoint the wildcard record points at"
  value       = module.sites.endpoint_host_name
}

output "server_client_id" {
  description = "The service principal kinotic-server runs as (AZURE_CLIENT_ID)"
  value       = azuread_application.server.client_id
}

output "server_client_secret" {
  description = "The principal's secret (AZURE_CLIENT_SECRET)"
  value       = azuread_application_password.server.value
  sensitive   = true
}

output "server_principal_object_id" {
  description = "Object id of the principal, for role assignments a root adds"
  value       = azuread_service_principal.server.object_id
}

output "tenant_id" {
  description = "The tenant the principal lives in (AZURE_TENANT_ID)"
  value       = data.azurerm_client_config.current.tenant_id
}

output "server_client_secret_key_id" {
  description = "Key id of the principal's secret; changes when the secret is rotated"
  value       = azuread_application_password.server.key_id
}

output "ui_hostnames" {
  description = "The platform UIs' hostnames served from the sites account, by key"
  value       = module.sites.hostnames
}

output "sites_storage_account_id" {
  description = "The sites storage account, for roles a root adds"
  value       = module.sites.storage_account_id
}

output "sites_frontdoor_profile_name" {
  description = "The Front Door profile serving the sites account"
  value       = module.sites.frontdoor_profile_name
}

output "sites_frontdoor_endpoint_name" {
  description = "Its endpoint"
  value       = module.sites.frontdoor_endpoint_name
}
