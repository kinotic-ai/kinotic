output "sites_domain" {
  description = "The domain every published UI is a label under"
  value       = local.sites_domain
}

output "endpoint_host_name" {
  description = "The Front Door endpoint the wildcard record points at"
  value       = azurerm_cdn_frontdoor_endpoint.sites.host_name
}

output "storage_blob_endpoint" {
  description = "The blob endpoint kinotic-server publishes sites into"
  value       = azurerm_storage_account.sites.primary_blob_endpoint
}

output "hostnames" {
  description = "The named hostnames served from the account, by the caller's key"
  value       = { for key, domain in azurerm_cdn_frontdoor_custom_domain.named : key => domain.host_name }
}

output "storage_account_id" {
  description = "The sites storage account, for roles a caller adds"
  value       = azurerm_storage_account.sites.id
}

output "frontdoor_profile_name" {
  description = "The Front Door profile, for a cache purge"
  value       = azurerm_cdn_frontdoor_profile.sites.name
}

output "frontdoor_endpoint_name" {
  description = "Its endpoint, for a cache purge"
  value       = azurerm_cdn_frontdoor_endpoint.sites.name
}
