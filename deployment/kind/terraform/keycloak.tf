# ── PostgreSQL (Keycloak database) ────────────────────────

resource "helm_release" "postgresql" {
  count = var.enable_keycloak ? 1 : 0

  name       = "keycloak-db"
  namespace  = "default"
  repository = "oci://registry-1.docker.io/bitnamicharts"
  chart      = "postgresql"
  version    = "18.5.11"
  wait       = true
  timeout    = var.deploy_timeout

  values = [file("${path.module}/../config/postgresql/values.yaml")]

  # Override credentials from variables (not hardcoded in values.yaml)
  set_sensitive {
    name  = "global.postgresql.auth.postgresPassword"
    value = var.keycloak_db_password
  }
  set_sensitive {
    name  = "global.postgresql.auth.username"
    value = var.keycloak_db_username
  }
  set_sensitive {
    name  = "global.postgresql.auth.password"
    value = var.keycloak_db_password
  }

  depends_on = [kind_cluster.kinotic]
}

# ── Keycloak Realm ConfigMap ──────────────────────────────

resource "kubernetes_config_map" "keycloak_realm" {
  count = var.enable_keycloak ? 1 : 0

  metadata {
    name      = "keycloak-realm"
    namespace = "default"
  }

  data = {
    "realm.json" = file("${path.module}/../../docker-compose/keycloak-test-realm.json")
  }

  depends_on = [kind_cluster.kinotic]
}

# ── Keycloak ──────────────────────────────────────────────

resource "helm_release" "keycloak" {
  count = var.enable_keycloak ? 1 : 0

  name      = "keycloak"
  namespace = "default"
  chart     = "${path.module}/../charts/keycloak"
  wait      = true
  timeout   = var.deploy_timeout

  values = [file("${path.module}/../config/keycloak/values.yaml")]

  set_sensitive {
    name  = "auth.adminPassword"
    value = var.keycloak_admin_password
  }

  set_sensitive {
    name  = "database.password"
    value = var.keycloak_db_password
  }

  depends_on = [
    helm_release.postgresql,
    kubernetes_config_map.keycloak_realm,
  ]
}
