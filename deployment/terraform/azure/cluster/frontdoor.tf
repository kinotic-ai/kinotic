# ── UI sites on Front Door ────────────────────────────────────────────────────
# Every published UI is served at <label>.apps.<zone> through this one Front Door Standard
# profile and endpoint, and the profile's identity, which reads every organization's storage
# account. kinotic-server (FrontDoorUiDeploymentProvisioner) creates the rest at runtime:
# per organization an origin group authenticating as that identity, with an origin on its
# storage account; once, the rule set that serves a single-page application's routes; per
# site a custom domain with a managed certificate, a route, and the CNAME and validation
# TXT records in the platform zone.

locals {
  sites_domain = "apps.${local.global.dns_zone_name}"
}

resource "azurerm_cdn_frontdoor_profile" "sites" {
  name                = "afd-${local.name_prefix}-sites"
  resource_group_name = azurerm_resource_group.main.name
  sku_name            = "Standard_AzureFrontDoor"
  tags                = local.common_tags

  identity {
    type = "SystemAssigned"
  }
}

# Every organization's account is created in the org storage group, so one assignment
# covers them all. The provider cannot read a new identity's principal id in the plan that
# creates it, so a fresh root applies the profile first:
#   terraform apply -target=azurerm_cdn_frontdoor_profile.sites && terraform apply
resource "azurerm_role_assignment" "sites_blob_reader" {
  scope                = azurerm_resource_group.org_storage.id
  role_definition_name = "Storage Blob Data Reader"
  principal_id         = azurerm_cdn_frontdoor_profile.sites.identity[0].principal_id
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
