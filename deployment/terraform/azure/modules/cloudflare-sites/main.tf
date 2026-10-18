# ── Published UI sites on Cloudflare ─────────────────────────────────────────
# Every organization's published UIs are served under its own subdomain of the sites domain,
# <app>--<ui>.<org>.<sites domain>, by one Worker bound to the site hosts, which reads each site
# from sites/<hostname>/ in the sites storage account. Nothing here is created per organization,
# application, UI or tenant: the platform creates one proxied wildcard record *.<org> per
# organization when the organization is created (CloudflareSiteDomainProvisioner), and the zone's
# Total TLS issues that organization's certificate from the record. The application API hosts,
# <org>--<app>.<apps_api_label>.<sites domain>, are proxied records to the app server that no
# Worker is bound to, so API calls and WebSockets reach the server directly; they share the
# sites domain so a site and its API are same-site and the session cookie flows.
#
# The resource schemas target the cloudflare provider's 5.x series; a renamed attribute shows up
# on the first plan, not at apply.

terraform {
  required_providers {
    cloudflare = {
      source = "cloudflare/cloudflare"
    }
  }
}

locals {
  sites_domain    = var.sites_subdomain == "" ? var.zone_name : "${var.sites_subdomain}.${var.zone_name}"
  apps_api_domain = "${var.apps_api_label}.${local.sites_domain}"
  # Only the zone itself is covered by the universal certificate; a sites domain within it serves no
  # organization landing host, since <org>.<subdomain>.<zone> is two labels under the zone
  organization_hosts_served = var.sites_subdomain == ""
}

# ── Zone settings ─────────────────────────────────────────────────────────────
# Full (strict) validates the app server's certificate, which cert-manager issues for the
# apps-api names; the sites are served by the Worker and need no origin.

resource "cloudflare_zone_setting" "ssl" {
  zone_id    = var.zone_id
  setting_id = "ssl"
  value      = "strict"
}

resource "cloudflare_zone_setting" "always_use_https" {
  zone_id    = var.zone_id
  setting_id = "always_use_https"
  value      = "on"
}

resource "cloudflare_zone_setting" "min_tls_version" {
  zone_id    = var.zone_id
  setting_id = "min_tls_version"
  value      = "1.2"
}

# Total TLS (Advanced Certificate Manager) issues a certificate for every proxied hostname the
# universal certificate does not cover: each organization's *.<org> record, and the apps-api
# names below the zone's first label
resource "cloudflare_total_tls" "sites" {
  zone_id               = var.zone_id
  enabled               = true
  certificate_authority = "google"
}

# ── Application API hosts ─────────────────────────────────────────────────────

resource "cloudflare_dns_record" "apps_api" {
  count = var.app_server_address == null ? 0 : 1

  zone_id = var.zone_id
  name    = local.apps_api_domain
  type    = "A"
  content = var.app_server_address
  proxied = true
  ttl     = 1
  comment = "The app server: the shared host the platform social sign-ins return to"
}

resource "cloudflare_dns_record" "app_api_hosts" {
  count = var.app_server_address == null ? 0 : 1

  zone_id = var.zone_id
  name    = "*.${local.apps_api_domain}"
  type    = "A"
  content = var.app_server_address
  proxied = true
  ttl     = 1
  comment = "Every application's API host, <org>--<app>"
}

# ── The sites Worker ──────────────────────────────────────────────────────────

resource "cloudflare_workers_script" "sites" {
  account_id  = var.account_id
  script_name = var.worker_name
  content     = file("${path.module}/../../../../cloudflare/sites-worker/worker.js")
  main_module = "worker.js"

  bindings = [
    { name = "SITES_DOMAIN", type = "plain_text", text = local.sites_domain },
    { name = "SITES_STORAGE_URL", type = "plain_text", text = trimsuffix(var.sites_storage_blob_endpoint, "/") },
    { name = "PORTAL_BASE_URL", type = "plain_text", text = var.portal_base_url },
  ]
}

# Every host under the sites domain is the Worker's: a site, a tenant's site, or an
# organization's own host
resource "cloudflare_workers_route" "sites" {
  zone_id = var.zone_id
  pattern = "*.${local.sites_domain}/*"
  script  = cloudflare_workers_script.sites.script_name
}

# The apps-api hosts are more specific routes bound to no Worker, so a request to an
# application's API goes straight to the app server
resource "cloudflare_workers_route" "apps_api" {
  zone_id = var.zone_id
  pattern = "${local.apps_api_domain}/*"
}

resource "cloudflare_workers_route" "app_api_hosts" {
  zone_id = var.zone_id
  pattern = "*.${local.apps_api_domain}/*"
}
