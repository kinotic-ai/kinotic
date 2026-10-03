# ── OpenFGA (the authorization engine) ────────────────────────────────────────
# The engine every server checks against, with a Postgres of its own on a zone-redundant disk,
# from the upstream chart with the values in deployment/helm/openfga. The kinotic release's
# store Job then creates the platform store in it before the servers start. The engine is
# reached only inside the cluster, by its ClusterIP Service.

resource "random_password" "openfga_db" {
  length  = 32
  special = false
}

resource "kubernetes_secret" "openfga_db" {
  metadata {
    name      = "openfga-db"
    namespace = kubernetes_namespace.kinotic.metadata[0].name
  }

  # What the database container initializes itself with, and the URI the engine connects with
  data = {
    POSTGRES_DB       = "openfga"
    POSTGRES_USER     = "openfga"
    POSTGRES_PASSWORD = random_password.openfga_db.result
    uri               = "postgres://openfga:${random_password.openfga_db.result}@openfga-db:5432/openfga?sslmode=disable"
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

  depends_on = [
    kubernetes_secret.openfga_db,
    kubernetes_storage_class_v1.es_premium_zrs,
  ]
}
