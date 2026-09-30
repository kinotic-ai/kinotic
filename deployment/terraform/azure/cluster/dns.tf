# ── DNS Records ───────────────────────────────────────────────────────────────
# Each server's names → its LoadBalancer, which carries 443 to the server's gateway (REST,
# STOMP/WebSocket, the login and OIDC callback endpoints): api.<domain> the org server,
# system-api.<domain> the system server, and apps-api.<domain> with every application's
# <organizationId>--<applicationId>.apps-api.<domain> the app server.

locals {
  server_records = {
    "api"        = "kinotic-server-org"
    "system-api" = "kinotic-server-system"
    "apps-api"   = "kinotic-server-app"
    "*.apps-api" = "kinotic-server-app"
  }
}

data "kubernetes_service" "server" {
  for_each = toset(values(local.server_records))

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
