# ── UI sites on Front Door ────────────────────────────────────────────────────
# Every published UI is served at <label>.apps.<zone> through this one Front Door Standard
# profile and endpoint. kinotic-server (FrontDoorUiDeploymentProvisioner) creates the rest
# at runtime: per organization an origin group, an origin on its storage account and a
# rule set carrying a read-only SAS; per site a custom domain with a managed certificate,
# a route, and the CNAME and validation TXT records in the platform zone.

locals {
  sites_domain = "apps.${local.global.dns_zone_name}"
}

resource "azurerm_cdn_frontdoor_profile" "sites" {
  name                = "afd-${local.name_prefix}-sites"
  resource_group_name = azurerm_resource_group.main.name
  sku_name            = "Standard_AzureFrontDoor"
  tags                = local.common_tags
}

resource "azurerm_cdn_frontdoor_endpoint" "sites" {
  name                     = "sites-${local.name_prefix}"
  cdn_frontdoor_profile_id = azurerm_cdn_frontdoor_profile.sites.id
  tags                     = local.common_tags
}

output "sites_domain" {
  description = "The domain every published UI is a label under"
  value       = local.sites_domain
}

output "frontdoor_endpoint_host_name" {
  description = "The Front Door endpoint every site's CNAME points at"
  value       = azurerm_cdn_frontdoor_endpoint.sites.host_name
}
