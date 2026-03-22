# ── mkcert TLS for local development ─────────────────────
#
# Generates browser-trusted certificates using mkcert and
# creates the Kubernetes TLS secret that the ingress expects.
#
# Prerequisites:
#   brew install mkcert
#   mkcert -install          # one-time CA setup
#
# When use_mkcert = false, the Helm chart falls back to
# cert-manager self-signed certificates (browser will warn).

variable "use_mkcert" {
  description = "Generate browser-trusted certs with mkcert (requires mkcert installed + CA set up)"
  type        = bool
  default     = true
}

# Generate certificates via mkcert into a temp directory
resource "terraform_data" "mkcert" {
  count = var.use_mkcert ? 1 : 0

  provisioner "local-exec" {
    command = <<-EOT
      set -e
      CERT_DIR="${path.module}/.certs"
      mkdir -p "$CERT_DIR"
      mkcert -cert-file "$CERT_DIR/tls.crt" \
             -key-file  "$CERT_DIR/tls.key" \
             localhost kinotic.local 127.0.0.1 ::1

      # Export CA for CLI tools (Node.js, curl, etc.)
      CA_DIR="$HOME/.kinotic/kind"
      mkdir -p "$CA_DIR"
      cp "$(mkcert -CAROOT)/rootCA.pem" "$CA_DIR/ca.crt"
    EOT
  }

  depends_on = [kind_cluster.kinotic]
}

# Read the generated cert and key files
data "local_file" "tls_cert" {
  count    = var.use_mkcert ? 1 : 0
  filename = "${path.module}/.certs/tls.crt"

  depends_on = [terraform_data.mkcert]
}

data "local_file" "tls_key" {
  count    = var.use_mkcert ? 1 : 0
  filename = "${path.module}/.certs/tls.key"

  depends_on = [terraform_data.mkcert]
}

# Create the TLS secret the ingress expects
resource "kubernetes_secret" "kinotic_tls" {
  count = var.use_mkcert ? 1 : 0

  metadata {
    name      = "kinotic-tls-secret"
    namespace = "default"
  }

  type = "kubernetes.io/tls"

  data = {
    "tls.crt" = data.local_file.tls_cert[0].content
    "tls.key" = data.local_file.tls_key[0].content
  }

  depends_on = [terraform_data.mkcert]
}
