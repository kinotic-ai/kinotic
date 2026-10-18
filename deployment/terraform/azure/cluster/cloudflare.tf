# ── The customer domain on Cloudflare ────────────────────────────────────────
# Organizations' sites and the application API hosts live on the Cloudflare zone, kinotic.io,
# rather than the platform's own zone: the sites Worker serves every site under
# <app>--<ui>.<org>.<zone> from the sites account, each organization gets one proxied
# wildcard record the platform creates when the organization is created, and the app server
# is reached at <org>--<app>.apps-api.<zone> through proxied records to its load balancer, so a
# site and its API are same-site. Nothing here changes per organization or application.

# Derived from the variables rather than the module's outputs, so the servers' release, which the
# app server's load balancer address comes from, does not wait on the module that needs it
locals {
  cloudflare_sites_domain = var.cloudflare_zone_name
  apps_api_domain         = "apps-api.${var.cloudflare_zone_name}"
}

module "cloudflare_sites" {
  source = "../modules/cloudflare-sites"

  zone_id                     = var.cloudflare_zone_id
  zone_name                   = var.cloudflare_zone_name
  account_id                  = var.cloudflare_account_id
  worker_name                 = "${local.name_prefix}-sites"
  app_server_address          = data.kubernetes_service.server["kinotic-server-app"].status[0].load_balancer[0].ingress[0].ip
  sites_storage_blob_endpoint = module.sites.storage_blob_endpoint
  portal_base_url             = "https://portal.${local.global.dns_zone_name}"
}

# cert-manager answers the DNS-01 challenges for the apps-api names of the zone with this token
# (tls.tf), which holds DNS edit on the zone and nothing else
resource "kubernetes_secret" "cloudflare_dns_api_token" {
  metadata {
    name      = "cloudflare-dns-api-token"
    namespace = "cert-manager"
  }
  data = {
    "api-token" = var.cloudflare_dns_api_token
  }

  depends_on = [kubernetes_namespace.cert_manager]
}

output "sites_domain" {
  description = "The domain every organization's sites are under"
  value       = local.cloudflare_sites_domain
}

output "apps_api_domain" {
  description = "The domain every application's API host is a label under"
  value       = local.apps_api_domain
}
