# ── Organization storage ──────────────────────────────────────────────────────
# Each organization gets one storage account, created at runtime by kinotic-server
# (AzureOrganizationStorageProvisioner) the first time a deployment publishes a UI.
# Terraform owns what the accounts share: the resource group they are created in, the
# subnet their private endpoints are placed in, and the private DNS zone those
# endpoints register in so the platform resolves each account to its private address.

resource "azurerm_resource_group" "org_storage" {
  name     = "rg-${local.name_prefix}-org-storage"
  location = var.location
  tags     = local.common_tags
}

resource "azurerm_subnet" "private_endpoints" {
  name                 = "snet-${local.name_prefix}-private-endpoints"
  resource_group_name  = azurerm_resource_group.main.name
  virtual_network_name = module.networking.vnet_name
  address_prefixes     = [var.private_endpoint_subnet_cidr]
}

resource "azurerm_private_dns_zone" "blob" {
  name                = "privatelink.blob.core.windows.net"
  resource_group_name = azurerm_resource_group.org_storage.name
  tags                = local.common_tags
}

resource "azurerm_private_dns_zone_virtual_network_link" "blob" {
  name                  = "vnl-${local.name_prefix}-blob"
  resource_group_name   = azurerm_resource_group.org_storage.name
  private_dns_zone_name = azurerm_private_dns_zone.blob.name
  virtual_network_id    = module.networking.vnet_id
  tags                  = local.common_tags
}

output "org_storage_resource_group_name" {
  description = "Resource group holding every organization's storage account"
  value       = azurerm_resource_group.org_storage.name
}
