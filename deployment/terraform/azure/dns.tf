# ── DNS Records ───────────────────────────────────────────────────────────────
# Reads the dynamic LB IPs and creates DNS records.
# IPs only change if Services are deleted and recreated.

data "kubernetes_service" "kinotic_server" {
  metadata {
    name      = "kinotic-server"
    namespace = "kinotic"
  }
  depends_on = [helm_release.kinotic_server]
}

data "kubernetes_service" "grafana" {
  metadata {
    name      = "grafana"
    namespace = "observability"
  }
  depends_on = [helm_release.grafana]
}

# kinotic.ai → kinotic-server LB
resource "azurerm_dns_a_record" "root" {
  name                = "@"
  zone_name           = azurerm_dns_zone.main.name
  resource_group_name = azurerm_resource_group.main.name
  ttl                 = 300
  records             = [data.kubernetes_service.kinotic_server.status[0].load_balancer[0].ingress[0].ip]
}

# grafana.kinotic.ai → Grafana LB
resource "azurerm_dns_a_record" "grafana" {
  name                = "grafana"
  zone_name           = azurerm_dns_zone.main.name
  resource_group_name = azurerm_resource_group.main.name
  ttl                 = 300
  records             = [data.kubernetes_service.grafana.status[0].load_balancer[0].ingress[0].ip]
}
