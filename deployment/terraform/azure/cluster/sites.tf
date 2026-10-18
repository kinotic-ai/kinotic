# ── UI sites ─────────────────────────────────────────────────────────────────
# Every published UI is served at <app>--<ui>.<org>.<cloudflare zone> by the sites Worker from
# the sites storage account (cloudflare.tf). The sites module owns the account and nothing
# changes in storage when a UI is published; the platform's own UIs are hosted outside the
# cluster, so the module creates no Front Door profile here.

module "sites" {
  source = "../modules/sites"

  name_prefix                  = local.name_prefix
  location                     = var.location
  resource_group_name          = azurerm_resource_group.main.name
  tags                         = local.common_tags
  dns_zone_name                = local.global.dns_zone_name
  dns_zone_id                  = local.global.dns_zone_id
  dns_zone_resource_group_name = local.global.resource_group_name
  server_principal_id          = azurerm_user_assigned_identity.kinotic_server.principal_id
}

output "sites_storage_blob_endpoint" {
  description = "The blob endpoint the system server publishes sites into"
  value       = module.sites.storage_blob_endpoint
}
