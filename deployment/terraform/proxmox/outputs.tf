output "proxmox_host" {
  description = "The host, for sync-secrets.sh and ssh"
  value       = var.proxmox_host
}

output "api_hostname" {
  description = "The API's hostname; the router forwards its 443 to server_ip's"
  value       = local.azure.api_hostname
}

output "portal_hostname" {
  description = "The portal, served by Front Door"
  value       = local.azure.portal_hostname
}

output "console_hostname" {
  description = "The system console, served by Front Door"
  value       = local.azure.console_hostname
}

output "server_ip" {
  description = "kinotic-server's LAN address, which the router forwards 443 to"
  value       = local.server_ip
}

output "grafana_url" {
  description = "Grafana, on the LAN, admin with the password in grafana.env"
  value       = "http://${local.grafana_ip}:3000"
}

# Each node adds its own KINOTIC_NODE_ID line; the machine credentials go in
# vm-manager.secrets.env beside it (deployment/vm-node/README.md). The server is named as
# its certificate names it, and the node resolves that name to the LAN address (hosts_entry)
output "vm_manager_env" {
  description = "The nodes' /etc/kinotic/vm-manager.env: the server and the stores as the nodes reach them"
  value       = <<-EOT
    KINOTIC_VM_PROVIDER=CLOUD_HYPERVISOR
    KINOTIC_SERVER_HOST=${local.azure.api_hostname}
    KINOTIC_SERVER_PORT=${var.api_port}
    KINOTIC_SERVER_USE_SSL=true
    KINOTIC_WORKLOAD_DATA_DIR=/var/lib/kinotic/workloads
    KINOTIC_WORKLOAD_DNS=${var.dns_servers[0]}
    KINOTIC_LOKI_URL=${local.service_urls["http://loki:3100"]}
    KINOTIC_TEMPO_URL=http://${local.tempo_ip}:4318
    KINOTIC_MIMIR_URL=${local.service_urls["http://mimir:9009"]}/otlp
  EOT
}

output "containers" {
  description = "Each container's vmid, for pct"
  value       = { for name, c in local.containers : name => c.vm_id }
}

output "hosts_entry" {
  description = "The /etc/hosts line each node carries, so the server's name verifies against its certificate and resolves on the LAN"
  value       = "${local.server_ip} ${local.azure.api_hostname}"
}
