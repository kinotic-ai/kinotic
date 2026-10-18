output "sites_domain" {
  description = "The domain every organization's sites are under: <app>--<ui>.<org>.<sites domain>"
  value       = local.sites_domain
}

output "apps_api_domain" {
  description = "The domain every application's API host is a label under, and the app server's shared host"
  value       = local.apps_api_domain
}

output "organization_hosts_served" {
  description = "Whether <org>.<sites domain> is served, which the universal certificate covers only when the sites domain is the zone"
  value       = local.organization_hosts_served
}
