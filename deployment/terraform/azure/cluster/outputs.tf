# ── Outputs ───────────────────────────────────────────────────────────────────

output "cluster_name" {
  description = "AKS cluster name"
  value       = module.aks.cluster_name
}

output "resource_group_name" {
  description = "Cluster resource group name"
  value       = azurerm_resource_group.main.name
}

output "get_credentials" {
  description = "Command to configure kubectl"
  value       = "az aks get-credentials --resource-group ${azurerm_resource_group.main.name} --name ${module.aks.cluster_name}"
}

output "elasticsearch_internal_endpoint" {
  description = "Elasticsearch endpoint (cluster-internal)"
  value       = "https://kinotic-es-es-http.elastic.svc:9200"
}

output "get_elastic_password" {
  description = "Command to retrieve the elastic user password"
  value       = "kubectl get secret kinotic-es-es-elastic-user -n elastic -o jsonpath='{.data.elastic}' | base64 -d"
}

# ── Firecracker (conditional) ────────────────────────────────────────────────

output "firecracker_public_ips" {
  description = "Firecracker VM public IPs"
  value       = var.enable_firecracker ? module.firecracker[0].public_ips : []
}

output "firecracker_ssh_commands" {
  description = "SSH commands for Firecracker VMs"
  value       = var.enable_firecracker ? module.firecracker[0].ssh_commands : []
}

# ── DNS (from global) ────────────────────────────────────────────────────────

output "dns_nameservers" {
  description = "Azure DNS nameservers — set these at your domain registrar"
  value       = local.global.dns_nameservers
}
