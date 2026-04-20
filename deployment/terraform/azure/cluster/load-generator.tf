# ── Load Generator (optional) ─────────────────────────────────────────────────
# Deploy with: terraform apply -var="enable_load_generator=true"

resource "helm_release" "load_generator" {
  count = var.enable_load_generator ? 1 : 0

  name      = "load-generator"
  namespace = kubernetes_namespace.kinotic.metadata[0].name
  chart     = "${path.module}/../../../helm/load-generator"
  wait      = false # Job runs asynchronously

  values = [file("${path.module}/config/load-generator/values.yaml")]

  depends_on = [helm_release.kinotic_server]
}
