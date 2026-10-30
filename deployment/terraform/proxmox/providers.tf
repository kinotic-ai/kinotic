provider "proxmox" {
  endpoint = "https://${var.proxmox_host}:8006/"
  username = "root@pam"
  password = var.proxmox_password
  # Verified by default; -var proxmox_insecure=true is the explicit exception for an installer certificate the
  # operator has not trusted
  insecure = var.proxmox_insecure

  # Snippets are uploaded over SSH, not the API: the host's root key must be in the agent
  # of whoever runs terraform. The container applier runs over the same SSH.
  ssh {
    agent    = true
    username = "root"
  }
}
