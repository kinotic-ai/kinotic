# ── Platform secrets (KinD) ───────────────────────────────
# Generates the JWT signing keys and secret-storage master keys as a K8s Secret that
# kinotic-server mounts as a volume. Matches the shape produced by the Azure Key Vault
# CSI driver, so the helm chart's pod spec and file-watch code path are identical across
# environments — only the source of the Secret differs.

resource "random_id" "jwt_signing_key_v1" {
  byte_length = 32
}

resource "random_id" "secret_storage_master_key_v1" {
  byte_length = 32
}

locals {
  jwt_signing_keys_json = jsonencode({
    activeKeyId = "v1"
    keys = [
      { id = "v1", key = random_id.jwt_signing_key_v1.b64_std },
    ]
  })

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

  data = {
    "kinotic-jwt-signing-keys"           = local.jwt_signing_keys_json
    "kinotic-secret-storage-master-keys" = local.secret_storage_master_keys_json
  }
}
