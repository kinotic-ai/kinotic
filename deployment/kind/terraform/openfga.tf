# ── OpenFGA (the authorization engine) ────────────────────
# The engine every server checks against, with a Postgres of its own, from the upstream chart
# with the values in deployment/helm/openfga. The kinotic release's store Job then creates the
# platform store in it before the servers start.

resource "kubernetes_secret" "openfga_db" {
  metadata {
    name      = "openfga-db"
    namespace = kubernetes_namespace.kinotic.metadata[0].name
  }

  # What the database container initializes itself with, and the URI the engine connects with
  data = {
    POSTGRES_DB       = "openfga"
    POSTGRES_USER     = var.openfga_db_username
    POSTGRES_PASSWORD = var.openfga_db_password
    uri               = "postgres://${var.openfga_db_username}:${var.openfga_db_password}@openfga-db:5432/openfga?sslmode=disable"
  }

  depends_on = [kind_cluster.kinotic]
}

resource "helm_release" "openfga" {
  name       = "openfga"
  namespace  = kubernetes_namespace.kinotic.metadata[0].name
  repository = "https://openfga.github.io/helm-charts"
  chart      = "openfga"
  version    = "0.3.15"
  wait       = true
  timeout    = var.deploy_timeout

  values = [
    file("${path.module}/../../helm/openfga/values.yaml"),
    file("${path.module}/../../helm/openfga/values-kind.yaml"),
  ]

  depends_on = [kubernetes_secret.openfga_db]
}
