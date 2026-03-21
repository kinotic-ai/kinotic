# ── Kinotic Server ────────────────────────────────────────

resource "helm_release" "kinotic_server" {
  name      = "kinotic-server"
  namespace = "default"
  chart     = "${path.module}/../../helm/kinotic"
  wait      = true
  timeout   = 900 # Migration needs time for ES to become ready

  values = [file("${path.module}/../config/kinotic-server/values.yaml")]

  # Image tag from variable — no hardcoding in values.yaml
  set {
    name  = "image.tag"
    value = var.kinotic_version
  }

  # Pull from Docker Hub
  set {
    name  = "image.pullPolicy"
    value = "IfNotPresent"
  }

  # Migration image tag matches server
  set {
    name  = "migration.image.tag"
    value = var.kinotic_version
  }

  set {
    name  = "migration.image.pullPolicy"
    value = "IfNotPresent"
  }

  # Give migration job more retries — ES may still be starting
  set {
    name  = "migration.backoffLimit"
    value = "10"
  }

  set {
    name  = "migration.activeDeadlineSeconds"
    value = "600"
  }

  # When Keycloak is enabled, add kubernetes-oidc profile and set oidc.enabled
  dynamic "set" {
    for_each = var.enable_keycloak ? [1] : []
    content {
      name  = "properties.springActiveProfiles"
      value = "production,kubernetes,kubernetes-oidc,debug,eviction-tracking"
    }
  }

  dynamic "set" {
    for_each = var.enable_keycloak ? [1] : []
    content {
      name  = "oidc.enabled"
      value = "true"
    }
  }

  depends_on = [
    helm_release.ingress_nginx,
    helm_release.cert_manager,
    helm_release.elasticsearch,
  ]
}
