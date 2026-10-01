# ── The workload nodes ────────────────────────────────────────────────────────
# Each worker is a VM on the LAN, beside the nodes on machines of their own, and is provisioned the
# same way: the node kit in deployment/vm-node over ssh, then the vm_manager_env output (README.md).
# The CPU is the host's, so the guest has the /dev/kvm that Cloud Hypervisor needs.

resource "proxmox_download_file" "worker_image" {
  count = length(var.workers) > 0 ? 1 : 0

  node_name    = var.proxmox_node
  datastore_id = var.files_datastore_id
  content_type = "import"
  url          = var.worker_image_url
  # Ubuntu's .img is a qcow2 image, which the import content type takes by its extension
  file_name = replace(basename(var.worker_image_url), ".img", ".qcow2")
  # A cloud image is republished in place; an existing worker keeps the disk it was created from
  overwrite = false
}

resource "proxmox_virtual_environment_file" "worker_user_data" {
  for_each = var.workers

  content_type = "snippets"
  datastore_id = var.files_datastore_id
  node_name    = var.proxmox_node

  source_raw {
    file_name = "kinotic-worker-${each.key}.user-data.yaml"
    data = templatefile("${path.module}/worker-user-data.yaml.tftpl", {
      hostname = each.key
      ssh_keys = var.worker_ssh_keys
    })
  }
}

resource "proxmox_virtual_environment_vm" "worker" {
  for_each = var.workers

  node_name   = var.proxmox_node
  vm_id       = each.value.vm_id
  name        = each.key
  description = "Kinotic workload node ${each.key}: Kata micro VMs on Cloud Hypervisor, provisioned with deployment/vm-node"
  tags        = ["kinotic", "dev-server", "worker"]
  on_boot     = true
  # Cloud Hypervisor runs each workload as a nested VM
  stop_on_destroy = true

  cpu {
    type  = "host"
    cores = each.value.cores
  }

  memory {
    dedicated = each.value.memory_mb
  }

  scsi_hardware = "virtio-scsi-single"

  disk {
    interface    = "scsi0"
    datastore_id = var.vm_datastore_id
    import_from  = proxmox_download_file.worker_image[0].id
    size         = 32
    discard      = "on"
    ssd          = true
  }

  disk {
    interface    = "scsi1"
    datastore_id = var.vm_datastore_id
    size         = each.value.docker_disk_gb
    discard      = "on"
    ssd          = true
  }

  disk {
    interface    = "scsi2"
    datastore_id = var.vm_datastore_id
    size         = each.value.workload_disk_gb
    discard      = "on"
    ssd          = true
  }

  network_device {
    bridge = var.bridge
  }

  operating_system {
    type = "l26"
  }

  serial_device {}

  initialization {
    datastore_id      = var.vm_datastore_id
    user_data_file_id = proxmox_virtual_environment_file.worker_user_data[each.key].id

    ip_config {
      ipv4 {
        address = each.value.ip
        gateway = var.gateway
      }
    }

    dns {
      servers = var.dns_servers
    }
  }

  lifecycle {
    # The node kit takes the VM from here; a new cloud image or user data is a new worker, not a rebuild
    ignore_changes = [initialization]
  }
}
