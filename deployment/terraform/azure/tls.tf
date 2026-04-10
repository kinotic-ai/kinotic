# ── Azure DNS Zone ────────────────────────────────────────────────────────────
# After apply, update your domain registrar (Namecheap) NS records to point
# to the Azure nameservers shown in: terraform output dns_nameservers

resource "azurerm_dns_zone" "main" {
  name                = var.domain_name
  resource_group_name = azurerm_resource_group.global.name
  tags                = local.common_tags
}

# NOTE: Management lock removed — CanNotDelete on the DNS zone also prevents
# cert-manager from cleaning up _acme-challenge TXT records during renewal.
# The DNS zone is protected by:
#   1. Being in a separate resource group (rg-kinotic-global)
#   2. Terraform's prevent_deletion_if_contains_resources feature flag
# For production, consider a lock with automation to temporarily remove during renewal.

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
  name      = "cert-manager-federated"
  user_assigned_identity_id = azurerm_user_assigned_identity.cert_manager.id
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

  # Enable workload identity on the cert-manager pod
  set = [
    { name = "installCRDs", value = "true" },
    { name = "serviceAccount.labels.azure\\.workload\\.identity/use", value = "true", type = "string" },
    { name = "serviceAccount.annotations.azure\\.workload\\.identity/client-id", value = azurerm_user_assigned_identity.cert_manager.client_id },
    { name = "podLabels.azure\\.workload\\.identity/use", value = "true", type = "string" },
  ]

  wait    = true
  timeout = 300

  depends_on = [
    kubernetes_namespace.cert_manager,
    module.aks,
  ]
}

# ── Let's Encrypt ClusterIssuer + Certificate ─────────────────────────────────
# These are cert-manager CRDs — kubernetes_manifest tries to validate at plan
# time before the cluster exists. Using kubectl apply instead.

resource "terraform_data" "cert_manager_issuer_and_cert" {
  provisioner "local-exec" {
    command = <<-EOT
      set -e

      echo "Creating Let's Encrypt ClusterIssuer..."
      kubectl apply -f - <<YAML
      apiVersion: cert-manager.io/v1
      kind: ClusterIssuer
      metadata:
        name: letsencrypt-prod
      spec:
        acme:
          server: https://acme-v02.api.letsencrypt.org/directory
          email: ${var.lets_encrypt_email}
          privateKeySecretRef:
            name: letsencrypt-prod-account-key
          solvers:
            - dns01:
                azureDNS:
                  subscriptionID: ${data.azurerm_client_config.tls.subscription_id}
                  resourceGroupName: ${azurerm_resource_group.global.name}
                  hostedZoneName: ${var.domain_name}
                  environment: AzurePublicCloud
                  managedIdentity:
                    clientID: ${azurerm_user_assigned_identity.cert_manager.client_id}
      YAML

      echo "Creating TLS Certificate..."
      kubectl apply -f - <<YAML
      apiVersion: cert-manager.io/v1
      kind: Certificate
      metadata:
        name: ${var.tls_secret_name}
        namespace: kinotic
      spec:
        secretName: ${var.tls_secret_name}
        issuerRef:
          name: letsencrypt-prod
          kind: ClusterIssuer
        dnsNames:
          - ${var.domain_name}
          - "*.${var.domain_name}"
      YAML

      echo "ClusterIssuer and Certificate created"
    EOT
  }

  depends_on = [
    helm_release.cert_manager,
    kubernetes_namespace.kinotic,
  ]
}

# ── Wait for TLS cert to be issued ───────────────────────────────────────────
# cert-manager issues the cert asynchronously after the Certificate resource
# is created. On first deploy, this requires DNS propagation (NS records
# pointing to Azure DNS). This resource blocks until the TLS secret exists
# with data, ensuring kinotic-server only starts after TLS is ready.

resource "terraform_data" "tls_cert_ready" {
  provisioner "local-exec" {
    command = <<-EOT
      set -e
      echo "Waiting for TLS certificate to be issued..."
      echo "If this is the first deploy, ensure NS records for ${var.domain_name} point to Azure DNS."
      echo "Check: terraform output dns_nameservers"
      for i in $(seq 1 120); do
        DATA=$(kubectl get secret ${var.tls_secret_name} -n kinotic \
          -o jsonpath='{.data.tls\.crt}' 2>/dev/null || true)
        if [ -n "$DATA" ]; then
          echo "TLS certificate is ready"
          exit 0
        fi
        echo "  Attempt $i/120 — cert not issued yet, waiting 30s..."
        sleep 30
      done
      echo "ERROR: Timed out waiting for TLS certificate (60 min)."
      echo "Check: kubectl describe certificate ${var.tls_secret_name} -n kinotic"
      exit 1
    EOT
  }

  depends_on = [terraform_data.cert_manager_issuer_and_cert]
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

  set = [
    { name = "reloader.watchGlobally", value = "true" },
  ]

  wait = true

  depends_on = [module.aks]
}

# ── Outputs ───────────────────────────────────────────────────────────────────

data "azurerm_client_config" "tls" {}
