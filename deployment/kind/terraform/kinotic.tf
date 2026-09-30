# ── Namespace ─────────────────────────────────────────────

resource "kubernetes_namespace" "kinotic" {
  metadata {
    name   = "kinotic"
    labels = { "app.kubernetes.io/managed-by" = "terraform" }
  }
  depends_on = [kind_cluster.kinotic]
}

# ── Kinotic servers ───────────────────────────────────────

locals {
  # The client secret the imported test realm gives kinotic-client, read from the realm file so the
  # org server and Keycloak cannot disagree on it
  keycloak_client_secret = one([
    for client in jsondecode(file("${path.module}/../../docker-compose/keycloak-test-realm.json")).clients :
    client.secret if client.clientId == "kinotic-client"
  ])
}

resource "helm_release" "kinotic" {
  name      = "kinotic"
  namespace = kubernetes_namespace.kinotic.metadata[0].name
  chart     = "${path.module}/../../helm/kinotic"
  wait      = true
  timeout   = 900 # Migration needs time for ES to become ready

  values = [file("${path.module}/../config/kinotic/values.yaml")]

  # Image tag, migration, TLS, and conditional OIDC sets
  set = concat(
    [
      # The servers' and the migration's image tag, from the variable
      { name = "image.tag", value = var.kinotic_version },
      # Give migration job more retries — ES may still be starting
      { name = "migration.backoffLimit", value = "10" },
      { name = "migration.activeDeadlineSeconds", value = "600" },
      # TLS — enable when mkcert is available
      { name = "tls.enabled", value = var.use_mkcert ? "true" : "false" },
      # The portal's URL, and the API behind it — switches scheme based on mkcert. KinD maps host 443 (TLS) and 9090 (plain).
      { name = "servers.kinotic-server-management.orgServer.portalBaseUrl", value = var.use_mkcert ? "https://localhost" : "http://localhost:9090" },
      { name = "servers.kinotic-server-management.orgServer.apiBaseUrl", value = var.use_mkcert ? "https://localhost" : "http://localhost:9090" },
      # The base every application's API host is a label under, on the app server's port, in the gateways' scheme:
      # what the app server serves, and what the system server hands each UI build
      { name = "servers.kinotic-server-app.appServer.apiBaseUrl", value = var.use_mkcert ? "https://localhost:58505" : "http://localhost:58505" },
      { name = "servers.kinotic-server-system.deployment.appApiBaseUrl", value = var.use_mkcert ? "https://localhost:58505" : "http://localhost:58505" },
      # The workloads dial the org and app servers in the gateways' scheme
      { name = "servers.kinotic-server-system.extraEnv.KINOTIC_SYSTEMAPI_DEPLOYMENT_ORGSERVER_USESSL", value = var.use_mkcert ? "true" : "false" },
      { name = "servers.kinotic-server-system.extraEnv.KINOTIC_SYSTEMAPI_DEPLOYMENT_APPSERVER_USESSL", value = var.use_mkcert ? "true" : "false" },
    ],
    # With Keycloak, the org server resolves the secret named "keycloak" on an OIDC configuration row to
    # the test realm's client secret, the way the compose Keycloak overlay does
    var.enable_keycloak ? [
      { name = "servers.kinotic-server-management.extraEnv.KINOTIC_AKV_KEYCLOAK", value = local.keycloak_client_secret },
    ] : [],
  )

  depends_on = [
    helm_release.elasticsearch,
    helm_release.es_secret_sync,
    kubernetes_secret.kinotic_tls,
    kubernetes_secret.platform_secrets,
  ]
}

moved {
  from = helm_release.kinotic_server
  to   = helm_release.kinotic
}
