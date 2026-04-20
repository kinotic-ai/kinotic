# Cluster configuration — destroy and rebuild freely.
# Global resources (DNS, Entra ID) are managed separately in global/.

environment        = "production"
project            = "kinotic"
location           = "centralus"
kubernetes_version = "1.32"

# ── Networking ────────────────────────────────────────────────────────────────
vnet_address_space = ["10.0.0.0/8"]
aks_subnet_cidr    = "10.1.0.0/16"
pod_cidr           = "192.168.0.0/16"
service_cidr       = "10.2.0.0/16"
dns_service_ip     = "10.2.0.10"

# ── System Node Pool ──────────────────────────────────────────────────────────
system_node_count = 3
system_vm_size    = "Standard_D4s_v5"
os_disk_size_gb   = 128

# ── Identity / RBAC ───────────────────────────────────────────────────────────
terraform_principal_object_id = "00000000-0000-0000-0000-000000000000"  # set in local.auto.tfvars

# ── Kinotic Server ────────────────────────────────────────────────────────────
kinotic_version = "4.2.0-SNAPSHOT"

# ── TLS ───────────────────────────────────────────────────────────────────────
lets_encrypt_email = ""  # set in local.auto.tfvars

# ── Tags ──────────────────────────────────────────────────────────────────────
tags = {
  environment = "production"
  project     = "kinotic"
  owner       = "platform-team"
  cost_center = "engineering"
}
