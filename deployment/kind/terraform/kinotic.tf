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

  # Image tag from variable
  set {
    name  = "image.tag"
    value = var.kinotic_version
  }

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

  # TLS — enable when mkcert is available
  set {
    name  = "tls.enabled"
    value = var.use_mkcert ? "true" : "false"
  }

  set {
    name  = "tls.secretName"
    value = "kinotic-tls"
  }

  # NodePort service with fixed ports matching KinD extraPortMappings
  set {
    name  = "service.type"
    value = "NodePort"
  }
  set {
    name  = "service.nodePorts.ui"
    value = "30443"
  }
  set {
    name  = "service.nodePorts.openApi"
    value = "30080"
  }
  set {
    name  = "service.nodePorts.graphql"
    value = "30400"
  }
  set {
    name  = "service.nodePorts.stomp"
    value = "30503"
  }

  # When Keycloak is enabled, add kubernetes-oidc profile and set oidc.enabled
  dynamic "set" {
    for_each = var.enable_keycloak ? [1] : []
    content {
      name  = "properties.springActiveProfiles"
      value = "production\\,kubernetes\\,kubernetes-oidc\\,debug\\,eviction-tracking"
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
    helm_release.elasticsearch,
    helm_release.es_secret_sync,
    kubernetes_secret.kinotic_tls,
  ]
}
