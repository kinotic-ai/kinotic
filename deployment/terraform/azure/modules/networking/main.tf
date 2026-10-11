variable "name_prefix" { type = string }
variable "location" { type = string }
variable "resource_group_name" { type = string }
variable "vnet_address_space" { type = list(string) }
variable "aks_subnet_cidr" { type = string }
variable "tags" { type = map(string) }
variable "aks_identity_principal_id" {
  description = "Principal ID of the kubelet identity — granted Network Contributor on the subnet"
  type        = string
}

variable "private_endpoints_subnet_cidr" {
  description = "CIDR for the subnet private endpoints are placed in"
  type        = string
}

variable "enable_firecracker" {
  description = "Create a subnet for Firecracker VM hosts"
  type        = bool
  default     = false
}

variable "firecracker_subnet_cidr" {
  description = "CIDR for the Firecracker host subnet"
  type        = string
  default     = "10.3.0.0/24"
}

# ── Virtual Network ──────────────────────────────────────────────────────────

resource "azurerm_virtual_network" "main" {
  name                = "vnet-${var.name_prefix}"
  location            = var.location
  resource_group_name = var.resource_group_name
  address_space       = var.vnet_address_space
  tags                = var.tags
}

# ── AKS Subnet ───────────────────────────────────────────────────────────────

resource "azurerm_subnet" "aks" {
  name                 = "snet-${var.name_prefix}-aks"
  resource_group_name  = var.resource_group_name
  virtual_network_name = azurerm_virtual_network.main.name
  address_prefixes     = [var.aks_subnet_cidr]
}

# NOTE: No custom NSG on the AKS subnet. AKS manages its own NSG in the MC_
# resource group with rules for LoadBalancer health probes and service ports.
# A custom empty NSG blocks all inbound internet traffic by default.

# ── Firecracker Subnet (conditional) ──────────────────────────────────────────

resource "azurerm_subnet" "firecracker" {
  count                = var.enable_firecracker ? 1 : 0
  name                 = "snet-${var.name_prefix}-firecracker"
  resource_group_name  = var.resource_group_name
  virtual_network_name = azurerm_virtual_network.main.name
  address_prefixes     = [var.firecracker_subnet_cidr]
}

# ── Private endpoints ─────────────────────────────────────────────────────────
# Storage accounts the cluster reaches only privately get their endpoint in this subnet, and
# resolve to it through the blob private DNS zone linked to the VNet.

resource "azurerm_subnet" "private_endpoints" {
  name                 = "snet-${var.name_prefix}-private-endpoints"
  resource_group_name  = var.resource_group_name
  virtual_network_name = azurerm_virtual_network.main.name
  address_prefixes     = [var.private_endpoints_subnet_cidr]
}

resource "azurerm_private_dns_zone" "blob" {
  name                = "privatelink.blob.core.windows.net"
  resource_group_name = var.resource_group_name
  tags                = var.tags
}

resource "azurerm_private_dns_zone_virtual_network_link" "blob" {
  name                  = "blob-${var.name_prefix}"
  resource_group_name   = var.resource_group_name
  private_dns_zone_name = azurerm_private_dns_zone.blob.name
  virtual_network_id    = azurerm_virtual_network.main.id
  tags                  = var.tags
}

# Resolves the managed Postgres servers reached through private endpoints, OpenFGA's among them
resource "azurerm_private_dns_zone" "postgres" {
  name                = "privatelink.postgres.database.azure.com"
  resource_group_name = var.resource_group_name
  tags                = var.tags
}

resource "azurerm_private_dns_zone_virtual_network_link" "postgres" {
  name                  = "postgres-${var.name_prefix}"
  resource_group_name   = var.resource_group_name
  private_dns_zone_name = azurerm_private_dns_zone.postgres.name
  virtual_network_id    = azurerm_virtual_network.main.id
  tags                  = var.tags
}

# ── RBAC: kubelet identity needs Network Contributor to manage LBs ────────────

resource "azurerm_role_assignment" "kubelet_network_contributor_subnet" {
  scope                = azurerm_subnet.aks.id
  role_definition_name = "Network Contributor"
  principal_id         = var.aks_identity_principal_id
}

resource "azurerm_role_assignment" "kubelet_network_contributor_vnet" {
  scope                = azurerm_virtual_network.main.id
  role_definition_name = "Network Contributor"
  principal_id         = var.aks_identity_principal_id
}

# ── Outputs ───────────────────────────────────────────────────────────────────

output "vnet_id" {
  value = azurerm_virtual_network.main.id
}

output "vnet_name" {
  value = azurerm_virtual_network.main.name
}

output "aks_subnet_id" {
  value = azurerm_subnet.aks.id
}

output "private_endpoints_subnet_id" {
  value = azurerm_subnet.private_endpoints.id
}

output "blob_private_dns_zone_id" {
  value = azurerm_private_dns_zone.blob.id
}

output "postgres_private_dns_zone_id" {
  value = azurerm_private_dns_zone.postgres.id
}

output "firecracker_subnet_id" {
  value = var.enable_firecracker ? azurerm_subnet.firecracker[0].id : null
}
