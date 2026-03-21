# ── NGINX Ingress Controller ──────────────────────────────

resource "helm_release" "ingress_nginx" {
  name             = "ingress-nginx"
  namespace        = "ingress-nginx"
  create_namespace = true
  repository       = "https://kubernetes.github.io/ingress-nginx"
  chart            = "ingress-nginx"
  version          = "4.12.0"
  wait             = true
  timeout          = var.deploy_timeout

  values = [file("${path.module}/../config/ingress-nginx/values.yaml")]

  depends_on = [kubernetes_labels.control_plane_ingress_ready]
}

# ── cert-manager ──────────────────────────────────────────

resource "helm_release" "cert_manager" {
  name             = "cert-manager"
  namespace        = "cert-manager"
  create_namespace = true
  repository       = "https://charts.jetstack.io"
  chart            = "cert-manager"
  version          = "v1.16.3"
  wait             = true
  timeout          = var.deploy_timeout

  values = [file("${path.module}/../config/cert-manager/values.yaml")]

  depends_on = [kind_cluster.kinotic]
}
