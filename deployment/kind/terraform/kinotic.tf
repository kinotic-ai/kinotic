# ── Namespace ─────────────────────────────────────────────

resource "kubernetes_namespace" "kinotic" {
  metadata {
    name   = "kinotic"
    labels = { "app.kubernetes.io/managed-by" = "terraform" }
  }
  depends_on = [kind_cluster.kinotic]
}

# ── Kinotic Server ────────────────────────────────────────

resource "helm_release" "kinotic_server" {
  name      = "kinotic-server"
  namespace = kubernetes_namespace.kinotic.metadata[0].name
  chart     = "${path.module}/../../helm/kinotic"
  wait      = true
  timeout   = 900 # Migration needs time for ES to become ready

  values = [file("${path.module}/../config/kinotic-server/values.yaml")]

  # Image tag, pull policy, migration, TLS, service, and conditional OIDC sets
  set = concat(
    [
      # Image tag from variable
      { name = "image.tag", value = var.kinotic_version },
      { name = "image.pullPolicy", value = "IfNotPresent" },
      # Migration image tag matches server
      { name = "migration.image.tag", value = var.kinotic_version },
      { name = "migration.image.pullPolicy", value = "IfNotPresent" },
      # Give migration job more retries — ES may still be starting
      { name = "migration.backoffLimit", value = "10" },
      { name = "migration.activeDeadlineSeconds", value = "600" },
      # TLS — enable when mkcert is available
      { name = "tls.enabled", value = var.use_mkcert ? "true" : "false" },
      { name = "tls.secretName", value = "kinotic-tls" },
      # Public URL — switches scheme based on mkcert. KinD maps host 443 (TLS) and 9090 (plain).
      { name = "kinotic.appBaseUrl", value = var.use_mkcert ? "https://localhost" : "http://localhost:9090" },
      # NodePort service with fixed ports matching KinD extraPortMappings
      { name = "service.type", value = "NodePort" },
      { name = "service.nodePorts.ui", value = "30443" },
      { name = "service.nodePorts.uiDirect", value = "30090" },
      { name = "service.nodePorts.openApi", value = "30080" },
      { name = "service.nodePorts.graphql", value = "30400" },
      { name = "service.nodePorts.stomp", value = "30503" },
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
