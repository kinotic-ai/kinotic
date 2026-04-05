# ── Azure DNS Zone ────────────────────────────────────────────────────────────
# After apply, update your domain registrar (Namecheap) NS records to point
# to the Azure nameservers shown in: terraform output dns_nameservers

resource "azurerm_dns_zone" "main" {
  name                = var.domain_name
  resource_group_name = azurerm_resource_group.main.name
  tags                = local.common_tags
}

# ── Managed Identity for cert-manager DNS-01 challenges ───────────────────────
# cert-manager uses this identity to create TXT records in Azure DNS
# for Let's Encrypt domain validation.

resource "azurerm_user_assigned_identity" "cert_manager" {
  name                = "id-${local.name_prefix}-cert-manager"
  location            = var.location
  resource_group_name = azurerm_resource_group.main.name
  tags                = local.common_tags
}

resource "azurerm_role_assignment" "cert_manager_dns_contributor" {
  scope                = azurerm_dns_zone.main.id
  role_definition_name = "DNS Zone Contributor"
  principal_id         = azurerm_user_assigned_identity.cert_manager.principal_id
}

# Federated credential so the cert-manager pod (via its ServiceAccount) can
# authenticate as this managed identity without any secrets.
resource "azurerm_federated_identity_credential" "cert_manager" {
  name                = "cert-manager-federated"
  resource_group_name = azurerm_resource_group.main.name
  parent_id           = azurerm_user_assigned_identity.cert_manager.id
  audience            = ["api://AzureADTokenExchange"]
  issuer              = data.azurerm_kubernetes_cluster.main.oidc_issuer_url
  subject             = "system:serviceaccount:cert-manager:cert-manager"
}

# ── cert-manager ──────────────────────────────────────────────────────────────

resource "kubernetes_namespace" "cert_manager" {
  metadata {
    name   = "cert-manager"
    labels = { "app.kubernetes.io/managed-by" = "terraform" }
  }
  depends_on = [module.aks]
}

resource "helm_release" "cert_manager" {
  name       = "cert-manager"
  repository = "https://charts.jetstack.io"
  chart      = "cert-manager"
  version    = "v1.16.3"
  namespace  = kubernetes_namespace.cert_manager.metadata[0].name

  set {
    name  = "installCRDs"
    value = "true"
  }

  # Enable workload identity on the cert-manager pod
  set {
    name  = "serviceAccount.labels.azure\\.workload\\.identity/use"
    value = "true"
  }
  set {
    name  = "serviceAccount.annotations.azure\\.workload\\.identity/client-id"
    value = azurerm_user_assigned_identity.cert_manager.client_id
  }
  set {
    name  = "podLabels.azure\\.workload\\.identity/use"
    value = "true"
  }

  wait    = true
  timeout = 300

  depends_on = [
    kubernetes_namespace.cert_manager,
    module.aks,
  ]
}

# ── Let's Encrypt ClusterIssuer (DNS-01 via Azure DNS) ────────────────────────

resource "kubernetes_manifest" "letsencrypt_issuer" {
  manifest = {
    apiVersion = "cert-manager.io/v1"
    kind       = "ClusterIssuer"
    metadata = {
      name = "letsencrypt-prod"
    }
    spec = {
      acme = {
        server = "https://acme-v2.api.letsencrypt.org/directory"
        email  = var.lets_encrypt_email
        privateKeySecretRef = {
          name = "letsencrypt-prod-account-key"
        }
        solvers = [{
          dns01 = {
            azureDNS = {
              subscriptionID    = data.azurerm_client_config.tls.subscription_id
              resourceGroupName = azurerm_resource_group.main.name
              hostedZoneName    = var.domain_name
              environment       = "AzurePublicCloud"
              managedIdentity = {
                clientID = azurerm_user_assigned_identity.cert_manager.client_id
              }
            }
          }
        }]
      }
    }
  }

  depends_on = [helm_release.cert_manager]
}

# ── TLS Certificate ───────────────────────────────────────────────────────────
# cert-manager will request a wildcard cert from Let's Encrypt and store
# it in the specified Kubernetes secret. Auto-renews ~30 days before expiry.

resource "kubernetes_manifest" "tls_certificate" {
  manifest = {
    apiVersion = "cert-manager.io/v1"
    kind       = "Certificate"
    metadata = {
      name      = var.tls_secret_name
      namespace = "kinotic"
    }
    spec = {
      secretName = var.tls_secret_name
      issuerRef = {
        name = "letsencrypt-prod"
        kind = "ClusterIssuer"
      }
      dnsNames = [
        var.domain_name,
        "*.${var.domain_name}",
      ]
    }
  }

  depends_on = [kubernetes_manifest.letsencrypt_issuer]
}

# ── Reloader ──────────────────────────────────────────────────────────────────
# Watches Kubernetes Secrets and ConfigMaps, triggers rolling restarts
# on Deployments that reference them via annotations.
# When cert-manager renews the TLS cert, Reloader restarts kinotic-server
# so Vert.x picks up the new cert/key files.

resource "helm_release" "reloader" {
  name       = "reloader"
  repository = "https://stakater.github.io/stakater-charts"
  chart      = "reloader"
  version    = "1.2.1"
  namespace  = "kube-system"

  set {
    name  = "reloader.watchGlobally"
    value = "true"
  }

  wait = true

  depends_on = [module.aks]
}

# ── Outputs ───────────────────────────────────────────────────────────────────

data "azurerm_client_config" "tls" {}
