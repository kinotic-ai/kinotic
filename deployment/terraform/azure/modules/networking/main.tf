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

# ── NSG ───────────────────────────────────────────────────────────────────────

resource "azurerm_network_security_group" "aks" {
  name                = "nsg-${var.name_prefix}-aks"
  location            = var.location
  resource_group_name = var.resource_group_name
  tags                = var.tags
}

resource "azurerm_subnet_network_security_group_association" "aks" {
  subnet_id                 = azurerm_subnet.aks.id
  network_security_group_id = azurerm_network_security_group.aks.id
}

# ── Firecracker Subnet (conditional) ──────────────────────────────────────────

resource "azurerm_subnet" "firecracker" {
  count                = var.enable_firecracker ? 1 : 0
  name                 = "snet-${var.name_prefix}-firecracker"
  resource_group_name  = var.resource_group_name
  virtual_network_name = azurerm_virtual_network.main.name
  address_prefixes     = [var.firecracker_subnet_cidr]
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

output "aks_subnet_id" {
  value = azurerm_subnet.aks.id
}

output "firecracker_subnet_id" {
  value = var.enable_firecracker ? azurerm_subnet.firecracker[0].id : null
}
