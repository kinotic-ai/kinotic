variable "name_prefix" { type = string }
variable "location" { type = string }
variable "resource_group_name" { type = string }
variable "kubernetes_version" { type = string }
variable "dns_prefix" { type = string }
variable "tags" { type = map(string) }

# Identity
variable "control_plane_identity_id" { type = string }
variable "kubelet_identity_id" { type = string }
variable "kubelet_identity_client_id" { type = string }
variable "kubelet_identity_object_id" { type = string }

# Networking
variable "aks_subnet_id" { type = string }
variable "pod_cidr" { type = string }
variable "service_cidr" { type = string }
variable "dns_service_ip" { type = string }
variable "beta_mode" { type = bool }

# System node pool
variable "system_node_count" { type = number }
variable "system_vm_size" { type = string }
variable "os_disk_size_gb" { type = number }

data "azurerm_client_config" "current" {}

# ── AKS Cluster ───────────────────────────────────────────────────────────────

resource "azurerm_kubernetes_cluster" "main" {
  name                = "aks-${var.name_prefix}"
  location            = var.location
  resource_group_name = var.resource_group_name
  dns_prefix          = var.dns_prefix
  kubernetes_version  = var.kubernetes_version
  tags                = var.tags
  sku_tier            = var.beta_mode ? "Free" : "Standard"

  # Disable static kubeconfig — all access via Azure RBAC
  local_account_disabled            = true
  role_based_access_control_enabled = true

  azure_active_directory_role_based_access_control {
    azure_rbac_enabled = true
    tenant_id          = data.azurerm_client_config.current.tenant_id
  }

  # Required for cert-manager workload identity (DNS-01 challenges)
  oidc_issuer_enabled       = true
  workload_identity_enabled = true

  # User-assigned identity for the control plane
  identity {
    type         = "UserAssigned"
    identity_ids = [var.control_plane_identity_id]
  }

  kubelet_identity {
    client_id                 = var.kubelet_identity_client_id
    object_id                 = var.kubelet_identity_object_id
    user_assigned_identity_id = var.kubelet_identity_id
  }

  # Azure CNI Overlay with Cilium — pods get IPs from pod_cidr, not the VNet.
  # Cilium replaces Calico as the network policy engine and enforces our
  # NetworkPolicy resources (e.g. ES ingress restrictions).
  network_profile {
    network_plugin      = "azure"
    network_plugin_mode = "overlay"
    network_data_plane  = "cilium"
    pod_cidr            = var.pod_cidr
    service_cidr        = var.service_cidr
    dns_service_ip      = var.dns_service_ip
  }

  # System node pool
  default_node_pool {
    name            = "system"
    node_count      = var.system_node_count
    vm_size         = var.system_vm_size
    os_disk_size_gb = var.os_disk_size_gb
    vnet_subnet_id  = var.aks_subnet_id
    zones           = ["1", "2", "3"]

    # In beta mode, all workloads share the system pool — no taint.
    # In production, only kube-system pods schedule here.
    only_critical_addons_enabled = var.beta_mode ? false : true

    upgrade_settings {
      max_surge = "1"
    }
  }

  auto_scaler_profile {
    balance_similar_node_groups = true
    expander                    = "least-waste"
  }
}

# ── Outputs ───────────────────────────────────────────────────────────────────

output "cluster_id" {
  value = azurerm_kubernetes_cluster.main.id
}

output "cluster_name" {
  value = azurerm_kubernetes_cluster.main.name
}
