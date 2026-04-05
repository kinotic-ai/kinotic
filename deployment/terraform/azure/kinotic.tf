# ── ES Secret Sync ────────────────────────────────────────────────────────────
# Copies the ECK elastic-user secret from elastic → kinotic namespace.

resource "helm_release" "es_secret_sync" {
  name      = "es-secret-sync"
  namespace = kubernetes_namespace.kinotic.metadata[0].name
  chart     = "${path.module}/../../helm/es-secret-sync"
  wait      = true
  timeout   = 300

  depends_on = [helm_release.eck_stack, kubernetes_namespace.kinotic]
}

# ── Kinotic Server ────────────────────────────────────────────────────────────

resource "helm_release" "kinotic_server" {
  name      = "kinotic-server"
  namespace = kubernetes_namespace.kinotic.metadata[0].name
  chart     = "${path.module}/../../helm/kinotic"
  wait      = true
  timeout   = 900

  values = [
    file("${path.module}/../../helm/kinotic/values.yaml"),
    file("${path.module}/config/kinotic-server/values.yaml"),
  ]

  # TLS — uses the Let's Encrypt cert from cert-manager
  set {
    name  = "tls.enabled"
    value = "true"
  }

  set {
    name  = "tls.secretName"
    value = var.tls_secret_name
  }

  # Image version — no SNAPSHOT tags in production
  set {
    name  = "image.tag"
    value = var.kinotic_version
  }

  set {
    name  = "migration.image.tag"
    value = var.kinotic_version
  }

  depends_on = [
    helm_release.eck_stack,
    helm_release.es_secret_sync,
    kubernetes_manifest.tls_certificate,
    helm_release.reloader,
  ]
}
