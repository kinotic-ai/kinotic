# ── Cluster ───────────────────────────────────────────────────────────────────

output "cluster_name" {
  value = module.aks.cluster_name
}

output "resource_group_name" {
  value = azurerm_resource_group.main.name
}

output "get_credentials" {
  description = "Run this to configure kubectl"
  value       = "az aks get-credentials --resource-group ${azurerm_resource_group.main.name} --name ${module.aks.cluster_name}"
}

# ── Elasticsearch ─────────────────────────────────────────────────────────────

output "elasticsearch_internal_endpoint" {
  description = "Internal HTTPS endpoint - reachable within the VNet only"
  value       = "http://kinotic-es-es-http.elastic.svc:9200"
}

output "get_elastic_password" {
  description = "Run this to retrieve the elastic user password after deploy"
  value       = "kubectl get secret kinotic-es-es-elastic-user -n elastic -o jsonpath='{.data.elastic}' | base64 -d"
}

# ── DNS ───────────────────────────────────────────────────────────────────────

output "dns_nameservers" {
  description = "Set these as NS records at your domain registrar (Namecheap)"
  value       = azurerm_dns_zone.main.name_servers
}

# ── Firecracker (conditional) ─────────────────────────────────────────────────

output "firecracker_ssh_commands" {
  description = "SSH commands to connect to Firecracker host VMs"
  value       = var.enable_firecracker ? module.firecracker[0].ssh_commands : []
}

output "firecracker_vm_public_ips" {
  description = "Public IPs of Firecracker host VMs"
  value       = var.enable_firecracker ? module.firecracker[0].vm_public_ips : []
}

output "firecracker_storage_account" {
  description = "Storage account for Firecracker VM images"
  value       = var.enable_firecracker ? module.firecracker[0].storage_account_name : null
}
