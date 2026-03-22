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

# ── CoreDNS: resolve kinotic.local inside the cluster ────
#
# Pods need to reach kinotic.local via the ingress controller.
# Without this, kinotic.local would resolve to 127.0.0.1 (the
# host loopback, unreachable from inside a pod).

data "kubernetes_service" "ingress_nginx" {
  metadata {
    name      = "ingress-nginx-controller"
    namespace = "ingress-nginx"
  }

  depends_on = [helm_release.ingress_nginx]
}

resource "terraform_data" "coredns" {
  input = data.kubernetes_service.ingress_nginx.spec[0].cluster_ip

  provisioner "local-exec" {
    command = <<-EOT
      set -e
      INGRESS_IP="${data.kubernetes_service.ingress_nginx.spec[0].cluster_ip}"
      kubectl --context "kind-${var.cluster_name}" apply -f - <<YAML
      apiVersion: v1
      kind: ConfigMap
      metadata:
        name: coredns
        namespace: kube-system
      data:
        Corefile: |
          .:53 {
            errors
            health {
                lameduck 5s
            }
            ready
            hosts {
              $INGRESS_IP kinotic.local
              fallthrough
            }
            kubernetes cluster.local in-addr.arpa ip6.arpa {
                pods insecure
                fallthrough in-addr.arpa ip6.arpa
                ttl 30
            }
            prometheus :9153
            forward . /etc/resolv.conf {
                max_concurrent 1000
            }
            cache 30 {
                disable success cluster.local
                disable denial cluster.local
            }
            loop
            reload
            loadbalance
          }
      YAML
    EOT
  }

  depends_on = [helm_release.ingress_nginx]
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
