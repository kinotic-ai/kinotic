# ── Platform secrets (KinD) ───────────────────────────────
# Generates each server's JWT signing keys, the secret-storage master key and the GitHub App's
# secrets as a K8s Secret the servers mount as a volume, each only its own key set. Holds the
# objects the Azure Key Vault holds, under the same names, so the helm chart's pod spec is
# identical across environments — only the source of the files differs.

locals {
  servers = ["kinotic-server-org", "kinotic-server-system", "kinotic-server-app"]
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

# The servers' modules were renamed; each key set follows its server's new name
moved {
  from = random_id.jwt_signing_key_v1["kinotic-org-server"]
  to   = random_id.jwt_signing_key_v1["kinotic-server-org"]
}

moved {
  from = random_id.jwt_signing_key_v1["kinotic-system-server"]
  to   = random_id.jwt_signing_key_v1["kinotic-server-system"]
}

moved {
  from = random_id.jwt_signing_key_v1["kinotic-app-server"]
  to   = random_id.jwt_signing_key_v1["kinotic-server-app"]
}

resource "random_id" "secret_storage_master_key" {
  byte_length = 32
}

moved {
  from = random_id.secret_storage_master_key_v1
  to   = random_id.secret_storage_master_key
}

locals {
  jwt_signing_keys_json = { for server in local.servers : server => jsonencode({
    activeKeyId = "v1"
    keys = [
      { id = "v1", key = random_id.jwt_signing_key_v1[server].b64_std },
    ]
  }) }
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
    {
      # SecretNameDeriver derives every stored secret's name from it, so every server holds the same key
      "kinotic-secret-storage-master-key" = random_id.secret_storage_master_key.b64_std
      # KinD runs no GitHub App: the placeholders the development profile uses too, which the org
      # and system servers boot with; a real App's values go here to exercise GitHub
      "kinotic-github-app-private-key" = "-"
      "kinotic-github-webhook-secret"  = "-"
    },
  )
}
