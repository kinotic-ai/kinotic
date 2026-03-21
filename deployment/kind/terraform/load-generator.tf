# ── Load Generator (optional) ─────────────────────────────

resource "helm_release" "load_generator" {
  count = var.enable_load_generator ? 1 : 0

  name      = "load-generator"
  namespace = "default"
  chart     = "${path.module}/../../helm/load-generator"
  wait      = false # Job runs asynchronously

  values = [file("${path.module}/../config/load-generator/values.yaml")]

  depends_on = [helm_release.kinotic_server]
}
