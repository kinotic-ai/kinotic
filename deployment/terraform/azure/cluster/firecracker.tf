# ── Firecracker VM Hosts (conditional) ────────────────────────────────────────
# Deploy with: terraform apply -var="enable_firecracker=true"
#
# Creates VM(s) with nested virtualization, KVM, and Firecracker installed.
# VMs share the same VNet as AKS so they can reach Elasticsearch and
# kinotic-server directly. Today workloads run as Kubernetes pods;
# Firecracker VMs will be used for secure multi-tenant customer workloads.

module "firecracker" {
  source = "../modules/firecracker"
  count  = var.enable_firecracker ? 1 : 0

  name_prefix         = local.name_prefix
  location            = var.location
  resource_group_name = azurerm_resource_group.main.name
  subnet_id           = module.networking.firecracker_subnet_id
  tags                = local.common_tags

  vm_size        = var.firecracker_vm_size
  node_count     = var.firecracker_node_count
  admin_username = var.firecracker_admin_username
  ssh_public_key = var.firecracker_ssh_public_key
}
