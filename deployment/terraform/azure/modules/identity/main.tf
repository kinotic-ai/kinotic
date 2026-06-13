variable "name_prefix" { type = string }
variable "location" { type = string }
variable "resource_group_name" { type = string }
variable "tags" { type = map(string) }

# The object ID of the principal running Terraform (service principal or user).
# Required to grant AKS RBAC Cluster Admin so the helm provider can connect.
# Get with: az ad signed-in-user show --query id -o tsv  (for user)
#       or: az ad sp show --id <client-id> --query id -o tsv  (for SP)
variable "terraform_principal_object_id" {
  description = "Object ID of the principal running Terraform (user or SP)"
  type        = string
}

# ── Control Plane Identity ────────────────────────────────────────────────────
# Used by the AKS control plane to manage Azure resources (LBs, NICs, disks)
resource "azurerm_user_assigned_identity" "control_plane" {
  name                = "id-${var.name_prefix}-aks-cp"
  location            = var.location
  resource_group_name = var.resource_group_name
  tags                = var.tags
}

# ── Kubelet Identity ──────────────────────────────────────────────────────────
# Used by kubelets on nodes to pull images, access disks, etc.
resource "azurerm_user_assigned_identity" "kubelet" {
  name                = "id-${var.name_prefix}-aks-kubelet"
  location            = var.location
  resource_group_name = var.resource_group_name
  tags                = var.tags
}

# ── Grant control plane identity the ability to manage the kubelet identity ───
# Required so the control plane can assign the kubelet identity to node VMs
resource "azurerm_role_assignment" "control_plane_managed_identity_operator" {
  scope                = azurerm_user_assigned_identity.kubelet.id
  role_definition_name = "Managed Identity Operator"
  principal_id         = azurerm_user_assigned_identity.control_plane.principal_id
}

# ── Outputs ───────────────────────────────────────────────────────────────────
output "control_plane_identity_id" {
  value = azurerm_user_assigned_identity.control_plane.id
}

output "kubelet_identity_id" {
  value = azurerm_user_assigned_identity.kubelet.id
}

output "kubelet_identity_client_id" {
  value = azurerm_user_assigned_identity.kubelet.client_id
}

output "kubelet_identity_object_id" {
  value = azurerm_user_assigned_identity.kubelet.principal_id
}

output "kubelet_identity_principal_id" {
  value = azurerm_user_assigned_identity.kubelet.principal_id
}

# ── AKS RBAC Role Assignments ─────────────────────────────────────────────────
# These two roles allow the principal running Terraform to authenticate to
# the AKS API server so the helm and kubernetes providers can connect.
# Required because local_account_disabled = true (no static kubeconfig).

# Grants full cluster admin — use only for the automation/platform principal.
# For human operators, prefer "Azure Kubernetes Service RBAC Reader" or a
# custom role scoped to specific namespaces.
resource "azurerm_role_assignment" "terraform_aks_cluster_admin" {
  # Scope is set at the resource group level — the AKS resource ID is not
  # available here, so we pass it in from the root module via an output.
  # This assignment is at RG scope so it applies to the AKS cluster inside it.
  scope                = "/subscriptions/${data.azurerm_client_config.current.subscription_id}/resourceGroups/${var.resource_group_name}"
  role_definition_name = "Azure Kubernetes Service RBAC Cluster Admin"
  principal_id         = var.terraform_principal_object_id
}

# Also grant the control plane identity AKS Cluster Admin on itself.
# This is required for the ECK operator to communicate back with the API server.
resource "azurerm_role_assignment" "control_plane_aks_cluster_admin" {
  scope                = "/subscriptions/${data.azurerm_client_config.current.subscription_id}/resourceGroups/${var.resource_group_name}"
  role_definition_name = "Azure Kubernetes Service RBAC Cluster Admin"
  principal_id         = azurerm_user_assigned_identity.control_plane.principal_id
}

data "azurerm_client_config" "current" {}

