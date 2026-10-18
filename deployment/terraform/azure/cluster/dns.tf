# ── DNS Records ───────────────────────────────────────────────────────────────
# Each server's names → its LoadBalancer, which carries 443 to the server's gateway (REST,
# STOMP/WebSocket, the login and OIDC callback endpoints): api.<domain> the management server
# and system-api.<domain> the system server, in the platform zone. The app server's names,
# apps-api.<zone> with every application's <organizationId>--<applicationId> under it, are in
# the Cloudflare zone (cloudflare.tf), proxied to the same kind of load balancer.

locals {
  server_records = {
    "api"        = "kinotic-server-management"
    "system-api" = "kinotic-server-system"
  }
  server_services = toset(concat(values(local.server_records), ["kinotic-server-app"]))
}

data "kubernetes_service" "server" {
  for_each = local.server_services

  metadata {
    name      = each.key
    namespace = "kinotic"
  }
  depends_on = [helm_release.kinotic]
}

resource "azurerm_dns_a_record" "server" {
  for_each = local.server_records

  name                = each.key
  zone_name           = local.global.dns_zone_name
  resource_group_name = local.global.resource_group_name
  ttl                 = 300
  records             = [data.kubernetes_service.server[each.value].status[0].load_balancer[0].ingress[0].ip]
}

moved {
  from = azurerm_dns_a_record.api
  to   = azurerm_dns_a_record.server["api"]
}
