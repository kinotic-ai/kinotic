# ──────────────────────────────────────────────────────────────────────────────
# Elasticsearch Node Pool
#
# This pool is tainted so ONLY ES pods (which tolerate the taint) land here.
# The system nodepool remains clean for kube-system workloads.
# ──────────────────────────────────────────────────────────────────────────────

resource "azurerm_kubernetes_cluster_node_pool" "elasticsearch" {
  count                 = var.beta_mode ? 0 : 1
  name                  = "esdata"
  kubernetes_cluster_id = module.aks.cluster_id
  vm_size               = "Standard_E8s_v5"   # 8 vCPU, 64 GB — memory optimised for ES
  node_count            = 3
  vnet_subnet_id        = module.networking.aks_subnet_id

  zones           = ["1", "2", "3"]
  os_disk_size_gb = 256
  os_disk_type    = "Managed"

  # Labels — used by nodeSelector in the ECK values.yaml
  node_labels = {
    "nodepool-type" = "elasticsearch"
    "workload"      = "esdata"
  }

  # Taint — only pods that tolerate this will schedule here.
  # The ECK values.yaml has the matching toleration defined.
  node_taints = [
    "workload=elasticsearch:NoSchedule"
  ]

  upgrade_settings {
    max_surge = "1"   # One extra node during rolling upgrades
  }

  # Node auto-repair is inherited from cluster settings (enabled by default)

  tags = local.common_tags

  lifecycle {
    ignore_changes = [
      # Prevent Terraform from reverting autoscaler-driven count changes
      node_count,
    ]
  }
}
