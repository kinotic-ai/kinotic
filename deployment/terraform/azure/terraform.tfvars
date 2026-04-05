# ── Dev Environment ───────────────────────────────────────────────────────────
# Apply with: terraform apply -var-file="environments/dev/terraform.tfvars"

environment        = "dev"
project            = "kinotic"
location           = "westus2"
kubernetes_version = "1.29"

# ── Networking ────────────────────────────────────────────────────────────────
vnet_address_space = ["10.0.0.0/8"]
aks_subnet_cidr    = "10.1.0.0/16"
pod_cidr           = "192.168.0.0/16"  # Overlay — doesn't consume VNet space
service_cidr       = "10.2.0.0/16"
dns_service_ip     = "10.2.0.10"

# ── System Node Pool ──────────────────────────────────────────────────────────
system_node_count = 3
system_vm_size    = "Standard_D4s_v5"
os_disk_size_gb   = 128

# ── Identity / RBAC ───────────────────────────────────────────────────────────
# Get your object ID with:
#   az ad signed-in-user show --query id -o tsv          (interactive user)
#   az ad sp show --id <client-id> --query id -o tsv     (service principal)
terraform_principal_object_id = "00000000-0000-0000-0000-000000000000"  # <-- replace

# ── Kinotic Server ────────────────────────────────────────────────────────────
kinotic_version = "4.2.0-SNAPSHOT"  # <-- pin to a release tag before production

# ── TLS / DNS ─────────────────────────────────────────────────────────────────
domain_name        = "kinotic.ai"
lets_encrypt_email = ""  # <-- replace with your email

# ── Tags ──────────────────────────────────────────────────────────────────────
tags = {
  environment = "dev"
  project     = "kinotic"
  owner       = "platform-team"
  cost_center = "engineering"
}
