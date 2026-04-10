# ── PostgreSQL (Keycloak database) ────────────────────────

resource "helm_release" "postgresql" {
  count = var.enable_keycloak ? 1 : 0

  name       = "keycloak-db"
  namespace  = kubernetes_namespace.kinotic.metadata[0].name
  repository = "oci://registry-1.docker.io/bitnamicharts"
  chart      = "postgresql"
  version    = "18.5.11"
  wait       = true
  timeout    = var.deploy_timeout

  values = [file("${path.module}/../config/postgresql/values.yaml")]

  # Override credentials from variables (not hardcoded in values.yaml)
  set_sensitive = [
    { name = "global.postgresql.auth.postgresPassword", value = var.keycloak_db_password },
    { name = "global.postgresql.auth.username", value = var.keycloak_db_username },
    { name = "global.postgresql.auth.password", value = var.keycloak_db_password },
  ]

  depends_on = [kind_cluster.kinotic]
}

# ── Keycloak Realm ConfigMap ──────────────────────────────

resource "kubernetes_config_map" "keycloak_realm" {
  count = var.enable_keycloak ? 1 : 0

  metadata {
    name      = "keycloak-realm"
    namespace = kubernetes_namespace.kinotic.metadata[0].name
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
  namespace = kubernetes_namespace.kinotic.metadata[0].name
  chart     = "${path.module}/../charts/keycloak"
  wait      = true
  timeout   = var.deploy_timeout

  values = [file("${path.module}/../config/keycloak/values.yaml")]

  set_sensitive = [
    { name = "auth.adminPassword", value = var.keycloak_admin_password },
    { name = "database.password", value = var.keycloak_db_password },
  ]

  # TLS — use mkcert cert when available
  set = [
    { name = "tls.enabled", value = var.use_mkcert ? "true" : "false" },
  ]

  depends_on = [
    helm_release.postgresql,
    kubernetes_config_map.keycloak_realm,
    kubernetes_secret.kinotic_tls,
  ]
}
