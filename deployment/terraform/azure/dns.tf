# ── DNS Records ───────────────────────────────────────────────────────────────
# Reads the dynamic LB IP assigned by Azure to the kinotic-server Service,
# then creates A records pointing kinotic.ai and *.kinotic.ai to it.
#
# The IP only changes if the Service is deleted and recreated. On re-apply,
# terraform updates the DNS record to match.

data "kubernetes_service" "kinotic_server" {
  metadata {
    name      = "kinotic-server"
    namespace = "kinotic"
  }

  depends_on = [helm_release.kinotic_server]
}

resource "azurerm_dns_a_record" "root" {
  name                = "@"
  zone_name           = azurerm_dns_zone.main.name
  resource_group_name = azurerm_resource_group.main.name
  ttl                 = 300
  records             = [data.kubernetes_service.kinotic_server.status[0].load_balancer[0].ingress[0].ip]
}

resource "azurerm_dns_a_record" "wildcard" {
  name                = "*"
  zone_name           = azurerm_dns_zone.main.name
  resource_group_name = azurerm_resource_group.main.name
  ttl                 = 300
  records             = [data.kubernetes_service.kinotic_server.status[0].load_balancer[0].ingress[0].ip]
}
