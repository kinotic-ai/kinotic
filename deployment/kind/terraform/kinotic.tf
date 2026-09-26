# ── Namespace ─────────────────────────────────────────────

resource "kubernetes_namespace" "kinotic" {
  metadata {
    name   = "kinotic"
    labels = { "app.kubernetes.io/managed-by" = "terraform" }
  }
  depends_on = [kind_cluster.kinotic]
}

# ── Kinotic servers ───────────────────────────────────────

resource "helm_release" "kinotic" {
  name      = "kinotic"
  namespace = kubernetes_namespace.kinotic.metadata[0].name
  chart     = "${path.module}/../../helm/kinotic"
  wait      = true
  timeout   = 900 # Migration needs time for ES to become ready

  values = [file("${path.module}/../config/kinotic/values.yaml")]

  # Image tag, migration, TLS, and conditional OIDC sets
  set = concat(
    [
      # The servers' and the migration's image tag, from the variable
      { name = "image.tag", value = var.kinotic_version },
      # Give migration job more retries — ES may still be starting
      { name = "migration.backoffLimit", value = "10" },
      { name = "migration.activeDeadlineSeconds", value = "600" },
      # TLS — enable when mkcert is available
      { name = "tls.enabled", value = var.use_mkcert ? "true" : "false" },
      # The portal's URL — switches scheme based on mkcert. KinD maps host 443 (TLS) and 9090 (plain).
      { name = "servers.kinotic-org-server.domain.appBaseUrl", value = var.use_mkcert ? "https://localhost" : "http://localhost:9090" },
    ],
    # When Keycloak is enabled, add kubernetes-oidc profile and set oidc.enabled
    var.enable_keycloak ? [
      { name = "properties.springActiveProfiles", value = "production\\,kubernetes\\,kubernetes-oidc\\,debug\\,eviction-tracking" },
      { name = "oidc.enabled", value = "true" },
    ] : [],
  )

  depends_on = [
    helm_release.elasticsearch,
    helm_release.es_secret_sync,
    kubernetes_secret.kinotic_tls,
    kubernetes_secret.platform_secrets,
  ]
}

moved {
  from = helm_release.kinotic_server
  to   = helm_release.kinotic
}
