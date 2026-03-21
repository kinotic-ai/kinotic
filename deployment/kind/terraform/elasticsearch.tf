# ── ECK Operator CRDs ─────────────────────────────────────

resource "helm_release" "eck_operator_crds" {
  name             = "elastic-operator-crds"
  namespace        = "elastic-system"
  create_namespace = true
  repository       = "https://helm.elastic.co"
  chart            = "eck-operator-crds"
  version          = "3.3.0"
  wait             = true

  depends_on = [kind_cluster.kinotic]
}

# ── ECK Operator ──────────────────────────────────────────

resource "helm_release" "eck_operator" {
  name             = "elastic-operator"
  namespace        = "elastic-system"
  create_namespace = true
  repository       = "https://helm.elastic.co"
  chart            = "eck-operator"
  version          = "3.3.0"
  wait             = true
  timeout          = var.deploy_timeout

  # CRDs are installed separately via eck_operator_crds
  set {
    name  = "installCRDs"
    value = "false"
  }

  values = [file("${path.module}/../config/eck-operator/values.yaml")]

  set {
    name  = "managedNamespaces"
    value = "{default}"
  }

  depends_on = [helm_release.eck_operator_crds]
}

# ── Elasticsearch ─────────────────────────────────────────

resource "helm_release" "elasticsearch" {
  name      = "elasticsearch"
  namespace = "default"
  chart     = "${path.module}/../../helm/elasticsearch"
  wait      = true
  timeout   = var.deploy_timeout

  # Base chart values + KinD-specific overrides
  values = [
    file("${path.module}/../../helm/elasticsearch/values.yaml"),
    file("${path.module}/../config/elasticsearch/values.yaml"),
  ]

  depends_on = [helm_release.eck_operator]
}
