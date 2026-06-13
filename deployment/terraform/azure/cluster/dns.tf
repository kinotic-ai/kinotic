# ── DNS Records ───────────────────────────────────────────────────────────────

data "kubernetes_service" "kinotic_server" {
  metadata {
    name      = "kinotic-server"
    namespace = "kinotic"
  }
  depends_on = [helm_release.kinotic_server]
}

# api.<domain> → kinotic-server LB (port 58503 hosts both STOMP/WebSocket and the
# /api/* REST endpoints — login, signup, OIDC callbacks).
resource "azurerm_dns_a_record" "api" {
  name                = "api"
  zone_name           = local.global.dns_zone_name
  resource_group_name = local.global.resource_group_name
  ttl                 = 300
  records             = [data.kubernetes_service.kinotic_server.status[0].load_balancer[0].ingress[0].ip]
}
