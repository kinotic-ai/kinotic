# ── OpenFGA (the authorization engine) ────────────────────────────────────────
# The engine every server checks against, from the upstream chart with the values in
# deployment/helm/openfga, keeping its relationships in an Azure Database for PostgreSQL flexible
# server. The database takes no public traffic: the engine reaches it through its private endpoint
# in the VNet, resolved by the privatelink zone the networking module links, the way the
# observability storage account is reached. The engine itself is reached only inside the cluster,
# by its ClusterIP Service, from the servers and the kinotic release's openfga-store Job, which
# creates the system store in it before the servers start; both present its preshared key.

resource "random_password" "openfga_db" {
  length  = 32
  special = false
}

resource "azurerm_postgresql_flexible_server" "openfga" {
  name                   = "psql-${local.name_prefix}-openfga"
  resource_group_name    = azurerm_resource_group.main.name
  location               = var.location
  version                = "17"
  administrator_login    = "openfga"
  administrator_password = random_password.openfga_db.result
  # Burstable for beta; general purpose with a standby in another zone for production
  sku_name                      = var.beta_mode ? "B_Standard_B1ms" : "GP_Standard_D2ds_v5"
  storage_mb                    = 32768
  backup_retention_days         = var.beta_mode ? 7 : 35
  public_network_access_enabled = false
  tags                          = local.common_tags

  dynamic "high_availability" {
    for_each = var.beta_mode ? [] : [1]
    content {
      mode = "ZoneRedundant"
    }
  }

  lifecycle {
    # A failover swaps the primary and standby zones, which the provider would otherwise undo
    ignore_changes = [zone, high_availability[0].standby_availability_zone]
  }
}

resource "azurerm_postgresql_flexible_server_database" "openfga" {
  name      = "openfga"
  server_id = azurerm_postgresql_flexible_server.openfga.id
  charset   = "UTF8"
  collation = "en_US.utf8"
}

resource "azurerm_private_endpoint" "openfga_db" {
  name                = "pe-psql-${local.name_prefix}-openfga"
  location            = var.location
  resource_group_name = azurerm_resource_group.main.name
  subnet_id           = module.networking.private_endpoints_subnet_id
  tags                = local.common_tags

  private_service_connection {
    name                           = "psql-${local.name_prefix}-openfga"
    private_connection_resource_id = azurerm_postgresql_flexible_server.openfga.id
    subresource_names              = ["postgresqlServer"]
    is_manual_connection           = false
  }

  private_dns_zone_group {
    name                 = "postgres"
    private_dns_zone_ids = [module.networking.postgres_private_dns_zone_id]
  }
}

resource "kubernetes_secret" "openfga_db" {
  metadata {
    name      = "openfga-db"
    namespace = kubernetes_namespace.kinotic.metadata[0].name
  }

  # The URI the engine and its migration connect with: the server's name, which the privatelink
  # zone resolves to the endpoint's address inside the VNet, over TLS
  data = {
    uri = "postgres://openfga:${urlencode(random_password.openfga_db.result)}@${azurerm_postgresql_flexible_server.openfga.fqdn}:5432/openfga?sslmode=require"
  }
}

resource "random_password" "openfga_preshared_key" {
  length  = 48
  special = false
}

# The preshared key every call to the engine's API carries, under the key the openfga chart reads
# (`keys`); the kinotic chart hands the same key to the servers and the openfga-store Job
resource "kubernetes_secret" "openfga_authn" {
  metadata {
    name      = "openfga-authn"
    namespace = kubernetes_namespace.kinotic.metadata[0].name
  }

  data = {
    keys = random_password.openfga_preshared_key.result
  }
}

resource "helm_release" "openfga" {
  name       = "openfga"
  namespace  = kubernetes_namespace.kinotic.metadata[0].name
  repository = "https://openfga.github.io/helm-charts"
  chart      = "openfga"
  version    = "0.3.15"
  wait       = true
  timeout    = 600

  values = [
    file("${path.module}/../../../helm/openfga/values.yaml"),
    file("${path.module}/../../../helm/openfga/values-azure.yaml"),
  ]

  # The beta B1ms server admits 35 user connections, and the engine opens up to 30 per pod by
  # default; 10 per pod fits three pods mid-rollout plus a migration
  set = var.beta_mode ? [{ name = "datastore.maxOpenConns", value = "10" }] : []

  depends_on = [
    kubernetes_secret.openfga_db,
    kubernetes_secret.openfga_authn,
    azurerm_postgresql_flexible_server_database.openfga,
    azurerm_private_endpoint.openfga_db,
  ]
}

# Only the servers and the openfga-store Job reach the engine

resource "kubernetes_network_policy" "openfga" {
  metadata {
    name      = "openfga-ingress"
    namespace = kubernetes_namespace.kinotic.metadata[0].name
  }

  spec {
    pod_selector {
      match_labels = {
        "app.kubernetes.io/name"     = "openfga"
        "app.kubernetes.io/instance" = "openfga"
      }
    }

    policy_types = ["Ingress"]

    ingress {
      # the servers' pods, labelled by the kinotic chart's server Deployment
      from {
        pod_selector {
          match_labels = { "app.kubernetes.io/part-of" = "kinotic" }
        }
      }
      # the openfga-store Job's pods
      from {
        pod_selector {
          match_labels = { "app" = "kinotic-openfga-store" }
        }
      }
    }
  }

  depends_on = [kubernetes_namespace.kinotic]
}
