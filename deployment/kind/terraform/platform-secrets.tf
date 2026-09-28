# ── Platform secrets (KinD) ───────────────────────────────
# Generates each server's JWT signing keys and the secret-storage master keys as a K8s Secret
# the servers mount as a volume, each only its own key set. Matches the shape produced by the
# Azure Key Vault CSI driver, so the helm chart's pod spec and file-watch code path are identical
# across environments — only the source of the Secret differs.

locals {
  servers = ["kinotic-org-server", "kinotic-system-server", "kinotic-app-server"]
}

resource "random_id" "jwt_signing_key_v1" {
  for_each    = toset(local.servers)
  byte_length = 32
}

# The org server keeps the key set the single server signed with
moved {
  from = random_id.jwt_signing_key_v1
  to   = random_id.jwt_signing_key_v1["kinotic-org-server"]
}

resource "random_id" "secret_storage_master_key_v1" {
  byte_length = 32
}

locals {
  jwt_signing_keys_json = { for server in local.servers : server => jsonencode({
    activeKeyId = "v1"
    keys = [
      { id = "v1", key = random_id.jwt_signing_key_v1[server].b64_std },
    ]
  }) }

  secret_storage_master_keys_json = jsonencode({
    activeKeyId = "v1"
    keys = [
      { id = "v1", key = random_id.secret_storage_master_key_v1.b64_std },
    ]
  })
}

resource "kubernetes_secret" "platform_secrets" {
  metadata {
    name      = "kinotic-platform-secrets"
    namespace = kubernetes_namespace.kinotic.metadata[0].name
    labels    = { "app.kubernetes.io/managed-by" = "terraform" }
  }

  type = "Opaque"

  data = merge(
    { for server, json in local.jwt_signing_keys_json : "${server}-jwt-signing-keys" => json },
    { "kinotic-secret-storage-master-keys" = local.secret_storage_master_keys_json },
  )
}
