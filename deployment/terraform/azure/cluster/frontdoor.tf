# ── UI sites on Front Door ────────────────────────────────────────────────────
# Every published UI is served at <label>.apps.<zone>. The sites module owns everything a
# site needs and nothing changes on Front Door or in DNS when a UI is published: the profile
# and endpoint, the sites storage account, the wildcard certificate and domain, the wildcard
# DNS record, and the origin, rules and route that serve sites/<hostname>/ from the account.

locals {
  sites_label  = "apps"
  sites_domain = module.sites.sites_domain
}

module "sites" {
  source = "../modules/sites"

  name_prefix                   = local.name_prefix
  location                      = var.location
  resource_group_name           = azurerm_resource_group.main.name
  tags                          = local.common_tags
  dns_zone_name                 = local.global.dns_zone_name
  dns_zone_id                   = local.global.dns_zone_id
  dns_zone_resource_group_name  = local.global.resource_group_name
  dns_zone_subscription_id      = local.global.subscription_id
  sites_label                   = local.sites_label
  key_vault_id                  = azurerm_key_vault.main.id
  certificate_officer_object_id = var.terraform_principal_object_id
  lets_encrypt_email            = var.lets_encrypt_email
  server_principal_id           = azurerm_user_assigned_identity.kinotic_server.principal_id
}

# The profile and endpoint predate the module and keep their identity
moved {
  from = azurerm_cdn_frontdoor_profile.sites
  to   = module.sites.azurerm_cdn_frontdoor_profile.sites
}

moved {
  from = azurerm_cdn_frontdoor_endpoint.sites
  to   = module.sites.azurerm_cdn_frontdoor_endpoint.sites
}

output "sites_domain" {
  description = "The domain every published UI is a label under"
  value       = local.sites_domain
}

output "frontdoor_endpoint_host_name" {
  description = "The Front Door endpoint the wildcard record points at"
  value       = module.sites.endpoint_host_name
}

output "sites_storage_blob_endpoint" {
  description = "The blob endpoint kinotic-server publishes sites into"
  value       = module.sites.storage_blob_endpoint
}
